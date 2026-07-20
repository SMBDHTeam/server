package com.server.schedule.planner;

import com.server.external.aitheme.PlaceThemePredictionClient;
import com.server.external.aitheme.PlaceThemePredictionClient.PlaceThemeInsight;
import com.server.external.openai.AiScheduleProposalClient;
import com.server.external.openai.OpenAiPlanningProperties;
import com.server.place.domain.Place;
import com.server.place.support.TourApiTheme;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiSchedulePlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiSchedulePlanGenerator.class);

    private final AiScheduleProposalClient client;
    private final OpenAiPlanningProperties properties;
    private final PlaceThemePredictionClient placeThemePredictionClient;

    public AiSchedulePlanGenerator(
            AiScheduleProposalClient client,
            OpenAiPlanningProperties properties
    ) {
        this(client, properties, place -> Optional.empty());
    }

    @Autowired
    public AiSchedulePlanGenerator(
            AiScheduleProposalClient client,
            OpenAiPlanningProperties properties,
            PlaceThemePredictionClient placeThemePredictionClient
    ) {
        this.client = client;
        this.properties = properties;
        this.placeThemePredictionClient = placeThemePredictionClient;
        log.info("AI schedule planner initialized: enabled={}, keyConfigured={}",
                properties.enabled(), !properties.apiKey().isBlank());
    }

    public Result generate(
            List<Place> candidates,
            Set<Long> mustVisitPlaceIds,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets,
            ScheduleCreateRequest request,
            Map<LocalDate, Set<Long>> fixedPlaceIdsByDate,
            String customPrompt
    ) {
        if (!properties.enabled()) {
            return insightProposal(
                    candidates, mustVisitPlaceIds, days, dailyStopTargets, request, fixedPlaceIdsByDate
            ).orElseGet(Result::ruleBased);
        }
        if (properties.apiKey().isBlank()) {
            return insightProposal(
                    candidates, mustVisitPlaceIds, days, dailyStopTargets, request, fixedPlaceIdsByDate
            ).orElseGet(Result::fallback);
        }
        try {
            AiScheduleProposalClient.Request proposalRequest = toRequest(
                    candidates, mustVisitPlaceIds, days, dailyStopTargets,
                    request, fixedPlaceIdsByDate, customPrompt);
            AiScheduleProposalClient.Proposal proposal = client.propose(proposalRequest);
            return validate(proposal, candidates, mustVisitPlaceIds, days,
                    dailyStopTargets, fixedPlaceIdsByDate);
        } catch (RuntimeException exception) {
            return Result.fallback();
        }
    }

    private Optional<Result> insightProposal(
            List<Place> candidates,
            Set<Long> mustVisitPlaceIds,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets,
            ScheduleCreateRequest request,
            Map<LocalDate, Set<Long>> fixedPlaceIdsByDate
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<CandidateInsight> informedCandidates = candidates.stream()
                .map(place -> placeThemePredictionClient.predictInsight(place)
                        .map(insight -> new CandidateInsight(place, insight)))
                .flatMap(Optional::stream)
                .toList();
        if (informedCandidates.isEmpty()) {
            log.debug("AI theme proposal skipped: no informed candidates");
            return Optional.empty();
        }
        Map<Long, CandidateInsight> insightByPlaceId = informedCandidates.stream()
                .collect(Collectors.toMap(candidate -> candidate.place().getId(), Function.identity()));
        Set<Long> selectedIds = new LinkedHashSet<>();
        List<AiScheduleProposalClient.DayProposal> dayProposals = new ArrayList<>();
        for (int index = 0; index < days.size(); index++) {
            ScheduleDay day = days.get(index);
            int targetStopCount = dailyStopTargets.get(index);
            Set<Long> requiredForDay = new LinkedHashSet<>(fixedPlaceIdsByDate.getOrDefault(day.getDate(), Set.of()));
            requiredForDay.addAll(mustVisitPlaceIds.stream()
                    .filter(placeId -> !selectedIds.contains(placeId))
                    .limit(Math.max(0, targetStopCount - requiredForDay.size()))
                    .toList());
            List<Long> chosen = new ArrayList<>();
            for (Long requiredPlaceId : requiredForDay) {
                if (selectedIds.add(requiredPlaceId)) {
                    chosen.add(requiredPlaceId);
                }
            }
            Set<String> usedClusters = chosen.stream()
                    .map(insightByPlaceId::get)
                    .filter(candidate -> candidate != null)
                    .map(candidate -> candidate.insight().clusterKey())
                    .filter(clusterKey -> clusterKey != null && !clusterKey.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            int requiredMeals = MealTimePolicy.requiredMealStops(day, targetStopCount);
            int chosenMealCount = (int) chosen.stream()
                    .map(insightByPlaceId::get)
                    .filter(candidate -> candidate != null && candidate.insight().mealPlace())
                    .count();
            while (chosen.size() < targetStopCount) {
                boolean mustPickMeal = chosenMealCount < requiredMeals
                        && (targetStopCount - chosen.size()) <= (requiredMeals - chosenMealCount);
                boolean mealPreferred = chosenMealCount < requiredMeals;
                CandidateInsight next = informedCandidates.stream()
                        .filter(candidate -> !selectedIds.contains(candidate.place().getId()))
                        .filter(candidate -> !mustPickMeal || candidate.insight().mealPlace())
                        .sorted(Comparator
                                .comparingInt((CandidateInsight candidate) -> insightPriority(
                                        candidate, request, mealPreferred))
                                .thenComparing((CandidateInsight candidate) ->
                                        clusterPenalty(candidate, usedClusters))
                                .thenComparingDouble(candidate -> endpointDistanceScore(candidate.place(), day))
                                .thenComparing(candidate -> candidate.place().getId()))
                        .findFirst()
                        .orElse(null);
                if (next == null) {
                    log.debug("AI theme proposal failed while selecting places: dayNo={}, chosen={}, target={}, requiredMeals={}",
                            day.getDayNo(), chosen.size(), targetStopCount, requiredMeals);
                    return Optional.empty();
                }
                selectedIds.add(next.place().getId());
                chosen.add(next.place().getId());
                if (next.insight().mealPlace()) {
                    chosenMealCount++;
                }
                if (next.insight().clusterKey() != null && !next.insight().clusterKey().isBlank()) {
                    usedClusters.add(next.insight().clusterKey());
                }
            }
            dayProposals.add(new AiScheduleProposalClient.DayProposal(day.getDayNo(), List.copyOf(chosen)));
        }
        Result result = validate(
                new AiScheduleProposalClient.Proposal(List.copyOf(dayProposals), 72, "Theme insight guided proposal"),
                candidates, mustVisitPlaceIds, days, dailyStopTargets, fixedPlaceIdsByDate
        );
        if (!result.hasProposal()) {
            log.debug("AI theme proposal validation failed: dayProposals={}", dayProposals);
            return Optional.empty();
        }
        log.info("AI theme proposal accepted: dayProposals={}", dayProposals);
        return Optional.of(new Result("AI_THEME_PROPOSED", 72, result.placesByDay()));
    }

    private int insightPriority(
            CandidateInsight candidate,
            ScheduleCreateRequest request,
            boolean mealPreferred
    ) {
        int priority = 8;
        for (ScheduleCreateRequest.SelectedAnswer answer : request.selectedAnswers()) {
            if (!"THEME".equals(answer.questionId())) {
                continue;
            }
            Optional<TourApiTheme> requestedTheme = TourApiTheme.fromAnswerId(answer.answerId());
            if (requestedTheme.isEmpty()) {
                continue;
            }
            if (candidate.insight().primaryTheme() == requestedTheme.get()) {
                priority = Math.min(priority, 0);
            } else if (candidate.insight().secondaryThemes().contains(requestedTheme.get())) {
                priority = Math.min(priority, 1);
            }
        }
        if (mealPreferred && candidate.insight().mealPlace()) {
            priority = Math.min(priority, 0);
        }
        if (lowMobilityRequested(request) && !candidate.insight().lowMobilityFriendly()) {
            priority += 3;
        }
        return priority;
    }

    private int clusterPenalty(CandidateInsight candidate, Set<String> usedClusters) {
        String clusterKey = candidate.insight().clusterKey();
        if (clusterKey == null || clusterKey.isBlank()) {
            return 0;
        }
        return usedClusters.contains(clusterKey) ? 1 : 0;
    }

    private double endpointDistanceScore(Place place, ScheduleDay day) {
        return distance(place.getLongitude(), place.getLatitude(),
                day.getStartLongitude(), day.getStartLatitude())
                + distance(place.getLongitude(), place.getLatitude(),
                day.getEndLongitude(), day.getEndLatitude());
    }

    private boolean lowMobilityRequested(ScheduleCreateRequest request) {
        return request.selectedAnswers().stream().anyMatch(answer ->
                "MOBILITY_LOW_WALK".equals(answer.answerId())
                        || "COMPANION_PARENTS".equals(answer.answerId())
                        || "COMPANION_FAMILY_WITH_CHILD".equals(answer.answerId()));
    }

    private double distance(
            BigDecimal fromLongitude,
            BigDecimal fromLatitude,
            BigDecimal toLongitude,
            BigDecimal toLatitude
    ) {
        if (fromLongitude == null || fromLatitude == null || toLongitude == null || toLatitude == null) {
            return Double.MAX_VALUE;
        }
        double deltaLongitude = fromLongitude.doubleValue() - toLongitude.doubleValue();
        double deltaLatitude = fromLatitude.doubleValue() - toLatitude.doubleValue();
        return (deltaLongitude * deltaLongitude) + (deltaLatitude * deltaLatitude);
    }

    private AiScheduleProposalClient.Request toRequest(
            List<Place> candidates,
            Set<Long> mustVisitPlaceIds,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets,
            ScheduleCreateRequest request,
            Map<LocalDate, Set<Long>> fixedPlaceIdsByDate,
            String customPrompt
    ) {
        List<AiScheduleProposalClient.Day> dayInputs = new ArrayList<>();
        for (int index = 0; index < days.size(); index++) {
            ScheduleDay day = days.get(index);
            dayInputs.add(new AiScheduleProposalClient.Day(
                    day.getDayNo(),
                    day.getDate().toString(),
                    day.getStartTime().toString(),
                    day.getEndTime().toString(),
                    day.getStartPlaceName(),
                    day.getEndPlaceName(),
                    dailyStopTargets.get(index),
                    MealTimePolicy.requiredMealStops(day, dailyStopTargets.get(index)),
                    List.copyOf(fixedPlaceIdsByDate.getOrDefault(day.getDate(), Set.of()))
            ));
        }
        List<AiScheduleProposalClient.Candidate> candidateInputs = candidates.stream()
                .map(place -> new AiScheduleProposalClient.Candidate(
                        place.getId(), place.getName(), place.getCategory(), place.getContentTypeId(),
                        place.getLongitude().toPlainString(), place.getLatitude().toPlainString(),
                        MealTimePolicy.isMealPlace(place)))
                .toList();
        List<AiScheduleProposalClient.SelectedAnswer> answers = request.selectedAnswers().stream()
                .map(answer -> new AiScheduleProposalClient.SelectedAnswer(
                        answer.questionId(), answer.answerId()))
                .toList();
        return new AiScheduleProposalClient.Request(
                List.copyOf(dayInputs), candidateInputs, List.copyOf(mustVisitPlaceIds),
                answers, customPrompt);
    }

    private Result validate(
            AiScheduleProposalClient.Proposal proposal,
            List<Place> candidates,
            Set<Long> mustVisitPlaceIds,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets,
            Map<LocalDate, Set<Long>> fixedPlaceIdsByDate
    ) {
        if (proposal == null || proposal.days() == null || proposal.days().size() != days.size()) {
            log.debug("AI proposal rejected: invalid day payload size. expectedDays={}, actualDays={}",
                    days.size(), proposal == null || proposal.days() == null ? null : proposal.days().size());
            return Result.fallback();
        }
        Map<Long, Place> placeById = candidates.stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<Integer, AiScheduleProposalClient.DayProposal> proposalByDay = new HashMap<>();
        for (AiScheduleProposalClient.DayProposal day : proposal.days()) {
            if (day == null || proposalByDay.put(day.dayNo(), day) != null) {
                log.debug("AI proposal rejected: duplicate or null day proposal. day={}", day);
                return Result.fallback();
            }
        }

        Set<Long> selectedIds = new LinkedHashSet<>();
        List<List<Place>> placesByDay = new ArrayList<>();
        for (int index = 0; index < days.size(); index++) {
            ScheduleDay scheduleDay = days.get(index);
            AiScheduleProposalClient.DayProposal proposedDay = proposalByDay.get(scheduleDay.getDayNo());
            if (proposedDay == null || proposedDay.placeIds() == null) {
                log.debug("AI proposal rejected: missing day proposal. dayNo={}", scheduleDay.getDayNo());
                return Result.fallback();
            }
            if (proposedDay.placeIds().size() != dailyStopTargets.get(index)) {
                log.debug("AI proposal rejected: wrong stop count. dayNo={}, expected={}, actual={}, placeIds={}",
                        scheduleDay.getDayNo(), dailyStopTargets.get(index),
                        proposedDay.placeIds().size(), proposedDay.placeIds());
                return Result.fallback();
            }
            if (new HashSet<>(proposedDay.placeIds()).size() != proposedDay.placeIds().size()) {
                log.debug("AI proposal rejected: duplicate place ids in day. dayNo={}, placeIds={}",
                        scheduleDay.getDayNo(), proposedDay.placeIds());
                return Result.fallback();
            }
            if (!placeById.keySet().containsAll(proposedDay.placeIds())) {
                log.debug("AI proposal rejected: unknown place ids. dayNo={}, placeIds={}",
                        scheduleDay.getDayNo(), proposedDay.placeIds());
                return Result.fallback();
            }
            if (proposedDay.placeIds().stream().anyMatch(selectedIds::contains)) {
                log.debug("AI proposal rejected: duplicate place ids across days. dayNo={}, placeIds={}",
                        scheduleDay.getDayNo(), proposedDay.placeIds());
                return Result.fallback();
            }
            selectedIds.addAll(proposedDay.placeIds());
            Set<Long> fixedForDay = fixedPlaceIdsByDate.getOrDefault(scheduleDay.getDate(), Set.of());
            if (!proposedDay.placeIds().containsAll(fixedForDay)) {
                log.debug("AI proposal rejected: fixed places missing. dayNo={}, requiredFixed={}, placeIds={}",
                        scheduleDay.getDayNo(), fixedForDay, proposedDay.placeIds());
                return Result.fallback();
            }
            List<Place> selected = proposedDay.placeIds().stream().map(placeById::get).toList();
            long mealCount = selected.stream().filter(this::isMealPlace).count();
            int requiredMealStops = MealTimePolicy.requiredMealStops(
                    scheduleDay, dailyStopTargets.get(index));
            if (mealCount < requiredMealStops) {
                log.debug("AI proposal rejected: insufficient meal places. dayNo={}, requiredMeals={}, actualMeals={}, placeIds={}",
                        scheduleDay.getDayNo(), requiredMealStops, mealCount, proposedDay.placeIds());
                return Result.fallback();
            }
            placesByDay.add(selected);
        }
        if (!selectedIds.containsAll(mustVisitPlaceIds)) {
            log.debug("AI proposal rejected: missing must-visit places. required={}, selected={}",
                    mustVisitPlaceIds, selectedIds);
            return Result.fallback();
        }
        return new Result("AI_PROPOSED", proposal.confidence(), List.copyOf(placesByDay));
    }

    private boolean isMealPlace(Place place) {
        if (MealTimePolicy.isMealPlace(place)) {
            return true;
        }
        return placeThemePredictionClient.predictInsight(place)
                .map(PlaceThemeInsight::mealPlace)
                .orElse(false);
    }

    public record Result(String source, Integer confidence, List<List<Place>> placesByDay) {

        public static Result ruleBased() {
            return new Result("RULE_BASED", null, List.of());
        }

        public static Result fallback() {
            return new Result("AI_FALLBACK", null, List.of());
        }

        public boolean hasProposal() {
            return !placesByDay.isEmpty();
        }
    }

    private record CandidateInsight(Place place, PlaceThemeInsight insight) {
    }
}
