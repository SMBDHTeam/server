package com.server.schedule.planner;

import com.server.external.openai.AiScheduleProposalClient;
import com.server.external.openai.OpenAiPlanningProperties;
import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiSchedulePlanGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiSchedulePlanGenerator.class);

    private final AiScheduleProposalClient client;
    private final OpenAiPlanningProperties properties;

    public AiSchedulePlanGenerator(
            AiScheduleProposalClient client,
            OpenAiPlanningProperties properties
    ) {
        this.client = client;
        this.properties = properties;
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
            return Result.ruleBased();
        }
        if (properties.apiKey().isBlank()) return Result.fallback();
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
            return Result.fallback();
        }
        Map<Long, Place> placeById = candidates.stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<Integer, AiScheduleProposalClient.DayProposal> proposalByDay = new HashMap<>();
        for (AiScheduleProposalClient.DayProposal day : proposal.days()) {
            if (day == null || proposalByDay.put(day.dayNo(), day) != null) {
                return Result.fallback();
            }
        }

        Set<Long> selectedIds = new LinkedHashSet<>();
        List<List<Place>> placesByDay = new ArrayList<>();
        for (int index = 0; index < days.size(); index++) {
            ScheduleDay scheduleDay = days.get(index);
            AiScheduleProposalClient.DayProposal proposedDay = proposalByDay.get(scheduleDay.getDayNo());
            if (proposedDay == null || proposedDay.placeIds() == null
                    || proposedDay.placeIds().size() != dailyStopTargets.get(index)
                    || new HashSet<>(proposedDay.placeIds()).size() != proposedDay.placeIds().size()
                    || !placeById.keySet().containsAll(proposedDay.placeIds())
                    || proposedDay.placeIds().stream().anyMatch(selectedIds::contains)) {
                return Result.fallback();
            }
            selectedIds.addAll(proposedDay.placeIds());
            Set<Long> fixedForDay = fixedPlaceIdsByDate.getOrDefault(scheduleDay.getDate(), Set.of());
            if (!proposedDay.placeIds().containsAll(fixedForDay)) {
                return Result.fallback();
            }
            List<Place> selected = proposedDay.placeIds().stream().map(placeById::get).toList();
            long mealCount = selected.stream().filter(MealTimePolicy::isMealPlace).count();
            if (mealCount < MealTimePolicy.requiredMealStops(
                    scheduleDay, dailyStopTargets.get(index))) {
                return Result.fallback();
            }
            placesByDay.add(selected);
        }
        if (!selectedIds.containsAll(mustVisitPlaceIds)) {
            return Result.fallback();
        }
        return new Result("AI_PROPOSED", proposal.confidence(), List.copyOf(placesByDay));
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
}
