package com.server.schedule.planner;

import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MultiDayPlanOptimizer {

    private static final int MEAL_COVERAGE_PENALTY = 1_000_000;
    private static final int EXCESS_MEAL_PENALTY = 250_000;
    private static final int PREFERENCE_COST_MULTIPLIER = 10;
    private static final int THEME_UNMET_PENALTY = 10_000;
    private static final int SAME_EXPERIENCE_PENALTY = 12_000;
    private static final int SAME_SEMANTIC_GROUP_PENALTY = 4_000;
    private static final int SIMILAR_EXPERIENCE_PROFILE_PENALTY = 12_000;
    private static final int CROSS_DAY_SAME_EXPERIENCE_PENALTY = 18_000;
    private static final int CROSS_DAY_SAME_SEMANTIC_GROUP_PENALTY = 6_000;
    private static final int CROSS_DAY_SIMILAR_PROFILE_PENALTY = 8_000;
    private static final int ROUTE_DISTANCE_COST_DIVISOR = 10;
    private static final int MAX_EXACT_CANDIDATE_POOL = 20;
    private static final int MAX_EXACT_TARGET_PLACES = 12;
    private static final int MAX_BEAM_GROUPS_PER_DAY = 192;
    private static final int MAX_BEAM_GROUPS_PER_CANDIDATE = 4;
    private static final int MAX_BEAM_GROUPS_PER_MEAL_MASK = 4;
    private static final int MAX_BEAM_GROUPS_PER_REQUIRED_MASK = 8;
    private static final int MAX_BEAM_STATES_PER_REQUIRED_MASK = 128;
    private static final int MAX_BEAM_STATES = 4_096;
    private static final int MAX_BEAM_INTERMEDIATE_MASKS = 131_072;
    private static final int MAX_BEAM_COMPATIBLE_GROUPS_PER_STATE = 64;
    private static final int MAX_RANKED_PLANS = 6;

    private final DayPlaceAllocator fallbackAllocator;
    private final PlacePreferenceScorer preferenceScorer;

    public MultiDayPlanOptimizer(
            DayPlaceAllocator fallbackAllocator,
            PlacePreferenceScorer preferenceScorer
    ) {
        this.fallbackAllocator = fallbackAllocator;
        this.preferenceScorer = preferenceScorer;
    }

    public List<List<Place>> optimize(
            List<Place> places,
            Set<Long> requiredPlaceIds,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets,
            ScheduleCreateRequest request
    ) {
        return ranked(places, requiredPlaceIds, days, dailyStopTargets, request, 1)
                .get(0)
                .placesByDay();
    }

    public List<OptimizedPlan> ranked(
            List<Place> places,
            Set<Long> requiredPlaceIds,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets,
            ScheduleCreateRequest request,
            int limit
    ) {
        return rankedWithPolicies(
                places,
                requiredPlaceIds,
                days,
                dailyStopTargets.stream().map(PlaceCountPolicy::exact).toList(),
                request,
                limit);
    }

    public List<List<Place>> optimizeWithPolicies(
            List<Place> places,
            Set<Long> requiredPlaceIds,
            List<ScheduleDay> days,
            List<PlaceCountPolicy> dailyPlaceCountPolicies,
            ScheduleCreateRequest request
    ) {
        return rankedWithPolicies(places, requiredPlaceIds, days, dailyPlaceCountPolicies, request, 1)
                .get(0)
                .placesByDay();
    }

    public List<OptimizedPlan> rankedWithPolicies(
            List<Place> places,
            Set<Long> requiredPlaceIds,
            List<ScheduleDay> days,
            List<PlaceCountPolicy> dailyPlaceCountPolicies,
            ScheduleCreateRequest request,
            int limit
    ) {
        if (days.size() != dailyPlaceCountPolicies.size()) {
            throw new IllegalArgumentException("Each schedule day requires a place count policy");
        }
        int rankedLimit = Math.max(1, Math.min(limit, MAX_RANKED_PLANS));
        int targetPlaceCount = dailyPlaceCountPolicies.stream()
                .mapToInt(PlaceCountPolicy::targetCount)
                .sum();
        int absoluteMinimumCount = dailyPlaceCountPolicies.stream()
                .mapToInt(PlaceCountPolicy::absoluteMinimum)
                .sum();
        int maximumPlaceCount = dailyPlaceCountPolicies.stream()
                .mapToInt(PlaceCountPolicy::maximum)
                .sum();
        List<Place> constrained = constrainedCandidates(
                places, requiredPlaceIds, targetPlaceCount, request);
        List<Place> candidates = boundedCandidates(constrained, requiredPlaceIds);
        if (candidates.size() < absoluteMinimumCount || candidates.size() > 62) {
            return List.of(fallbackPlan(
                    candidates, requiredPlaceIds, targetPlaceCount, days, dailyPlaceCountPolicies));
        }

        long requiredMask = requiredMask(candidates, requiredPlaceIds);
        long mealMask = mealMask(candidates);
        int totalRequiredMealStops = 0;
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            totalRequiredMealStops += MealTimePolicy.requiredMealStops(
                    days.get(dayIndex), dailyPlaceCountPolicies.get(dayIndex).targetCount());
        }
        boolean enforceMealCoverage = Long.bitCount(mealMask) >= totalRequiredMealStops;
        boolean beamSearch = maximumPlaceCount > MAX_EXACT_TARGET_PLACES;
        List<List<Group>> groupsByDay = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            List<Group> groups = groupsForDay(
                    candidates,
                    days.get(dayIndex),
                    dailyPlaceCountPolicies.get(dayIndex),
                    request,
                    enforceMealCoverage
            ).stream()
                    .sorted(groupComparator())
                    .toList();
            groupsByDay.add(beamSearch && dayIndex == 0
                    ? beamGroups(groups, candidates.size(), requiredMask, mealMask)
                    : groups);
        }

        Map<Long, List<PlanState>> states = new TreeMap<>();
        states.put(0L, List.of(new PlanState(
                PlanObjectiveEvaluator.empty(), 0L, 0L, 0L, List.of())));
        for (int dayIndex = 0; dayIndex < groupsByDay.size(); dayIndex++) {
            List<Group> dayGroups = groupsByDay.get(dayIndex);
            Map<Long, List<PlanState>> nextStates;
            if (beamSearch) {
                nextStates = combineBeamStates(
                        states, dayGroups, rankedLimit, requiredMask, mealMask);
            } else {
                nextStates = combineStates(states, dayGroups, rankedLimit);
            }
            states = beamSearch
                    ? pruneStates(nextStates, requiredMask, mealMask)
                    : nextStates;
        }

        List<OptimizedPlan> optimizedPlans = states.entrySet().stream()
                .filter(entry -> (entry.getKey() & requiredMask) == requiredMask)
                .flatMap(entry -> entry.getValue().stream())
                .map(state -> new OptimizedPlan(
                        state.legacyCost(),
                        state.objective(),
                        state.candidateSuitabilityCost(),
                        state.coordinateDistanceCost(),
                        state.placesByDay()))
                .sorted(Comparator.comparing(OptimizedPlan::objective)
                        .thenComparingLong(OptimizedPlan::candidateSuitabilityCost)
                        .thenComparingLong(OptimizedPlan::coordinateDistanceCost)
                        .thenComparing(plan -> planKey(plan.placesByDay())))
                .limit(rankedLimit)
                .toList();
        return optimizedPlans.isEmpty()
                ? List.of(fallbackPlan(
                        candidates, requiredPlaceIds, targetPlaceCount, days, dailyPlaceCountPolicies))
                : optimizedPlans;
    }

    private OptimizedPlan fallbackPlan(
            List<Place> candidates,
            Set<Long> requiredPlaceIds,
            int targetPlaceCount,
            List<ScheduleDay> days,
            List<PlaceCountPolicy> dailyPlaceCountPolicies
    ) {
        return new OptimizedPlan(
                Long.MAX_VALUE,
                fallbackObjective(),
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                fallbackAllocator.allocate(
                        fallbackCandidates(candidates, requiredPlaceIds, targetPlaceCount),
                        days,
                        dailyPlaceCountPolicies.stream().map(PlaceCountPolicy::targetCount).toList()));
    }

    private Map<Long, List<PlanState>> combineStates(
            Map<Long, List<PlanState>> states,
            List<Group> groups,
            int statesPerMask
    ) {
        Map<Long, List<PlanState>> nextStates = new TreeMap<>();
        for (Map.Entry<Long, List<PlanState>> stateEntry : states.entrySet()) {
            for (PlanState state : stateEntry.getValue()) {
                int compatibleGroupCount = 0;
                for (Group group : groups) {
                    if ((stateEntry.getKey() & group.mask()) != 0) continue;
                    addGroup(nextStates, stateEntry.getKey(), state, group, statesPerMask);
                    compatibleGroupCount++;
                    if (!state.placesByDay().isEmpty()
                            && compatibleGroupCount >= MAX_BEAM_COMPATIBLE_GROUPS_PER_STATE) {
                        break;
                    }
                }
            }
        }
        return nextStates;
    }

    private Map<Long, List<PlanState>> combineBeamStates(
            Map<Long, List<PlanState>> states,
            List<Group> groups,
            int statesPerMask,
            long requiredMask,
            long mealMask
    ) {
        Map<Long, List<PlanState>> nextStates = new TreeMap<>();
        for (Map.Entry<Long, List<PlanState>> stateEntry : states.entrySet()) {
            for (PlanState state : stateEntry.getValue()) {
                for (Group group : groups) {
                    addGroup(nextStates, stateEntry.getKey(), state, group, statesPerMask);
                }
            }
            if (nextStates.size() > MAX_BEAM_INTERMEDIATE_MASKS) {
                nextStates = pruneStates(nextStates, requiredMask, mealMask);
            }
        }
        return nextStates;
    }

    private void addGroup(
            Map<Long, List<PlanState>> nextStates,
            long stateMask,
            PlanState state,
            Group group,
            int statesPerMask
    ) {
        if ((stateMask & group.mask()) != 0) return;
        long nextMask = stateMask | group.mask();
        long crossDayDiversityCost = crossDayExperiencePenalty(state.placesByDay(), group.places());
        PlanObjective nextObjective = PlanObjectiveEvaluator.combine(
                state.objective(), group.objective());
        nextObjective = PlanObjectiveEvaluator.addDiversityCost(nextObjective, crossDayDiversityCost);
        long nextCandidateSuitabilityCost = state.candidateSuitabilityCost()
                + group.candidateSuitabilityCost();
        long nextCoordinateDistanceCost = state.coordinateDistanceCost()
                + group.coordinateDistanceCost();
        long nextLegacyCost = state.legacyCost() + group.legacyCost() + crossDayDiversityCost;
        List<List<Place>> assignments = new ArrayList<>(state.placesByDay());
        assignments.add(group.places());
        PlanState candidate = new PlanState(
                nextObjective,
                nextCandidateSuitabilityCost,
                nextCoordinateDistanceCost,
                nextLegacyCost,
                List.copyOf(assignments));
        List<PlanState> bucket = new ArrayList<>(
                nextStates.getOrDefault(nextMask, List.of()));
        String candidateKey = planKey(candidate.placesByDay());
        if (bucket.stream().anyMatch(existing -> planKey(existing.placesByDay()).equals(candidateKey))) {
            return;
        }
        bucket.add(candidate);
        bucket.sort(planStateComparator());
        nextStates.put(nextMask, List.copyOf(
                bucket.subList(0, Math.min(statesPerMask, bucket.size()))));
    }

    private long crossDayExperiencePenalty(List<List<Place>> previousDays, List<Place> currentDay) {
        long penalty = 0;
        for (List<Place> previousDay : previousDays) {
            for (Place previous : previousDay) {
                PlaceExperienceClassifier.ExperienceProfile previousProfile =
                        PlaceExperienceClassifier.classify(previous);
                if (!ExperienceSequenceEvaluator.isDiversityScored(previousProfile)) continue;
                for (Place current : currentDay) {
                    PlaceExperienceClassifier.ExperienceProfile currentProfile =
                            PlaceExperienceClassifier.classify(current);
                    if (!ExperienceSequenceEvaluator.isDiversityScored(currentProfile)) continue;
                    if (previousProfile.type() == currentProfile.type()) {
                        penalty += CROSS_DAY_SAME_EXPERIENCE_PENALTY;
                    }
                    if (previousProfile.semanticGroup() == currentProfile.semanticGroup()) {
                        penalty += CROSS_DAY_SAME_SEMANTIC_GROUP_PENALTY;
                    }
                    if (previousProfile.type() != currentProfile.type()) {
                        int similarity = PlaceExperienceClassifier.similarityPercent(
                                previousProfile, currentProfile);
                        if (similarity >= 65) {
                            penalty += (long) similarity * CROSS_DAY_SIMILAR_PROFILE_PENALTY / 100;
                        }
                    }
                }
            }
        }
        return penalty;
    }

    private List<Group> groupsForDay(
            List<Place> candidates,
            ScheduleDay day,
            PlaceCountPolicy placeCountPolicy,
            ScheduleCreateRequest request,
            boolean enforceMealCoverage
    ) {
        List<Group> groups = new ArrayList<>();
        ScheduleCreateRequest.Location start = location(
                day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude(), request.startLocation());
        ScheduleCreateRequest.Location end = location(
                day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude(), request.endLocation());
        DayCostContext costContext = new DayCostContext(
                candidates, start, end, request, preferenceScorer);
        int maximum = Math.min(placeCountPolicy.maximum(), candidates.size());
        for (int count = placeCountPolicy.absoluteMinimum(); count <= maximum; count++) {
            buildGroups(
                    candidates, day, placeCountPolicy, enforceMealCoverage, count, 0, 0L,
                    new ArrayList<>(), groups, costContext);
        }
        if (enforceMealCoverage) {
            groups.removeIf(group -> Long.bitCount(group.mask() & costContext.mealMask())
                    < MealTimePolicy.requiredMealStops(day, group.places().size()));
        }
        return List.copyOf(groups);
    }

    private void buildGroups(
            List<Place> candidates,
            ScheduleDay day,
            PlaceCountPolicy placeCountPolicy,
            boolean enforceMealCoverage,
            int remaining,
            int startIndex,
            long mask,
            List<Place> selected,
            List<Group> groups,
            DayCostContext costContext
    ) {
        if (remaining == 0) {
            List<Place> places = List.copyOf(selected);
            groups.add(group(day, placeCountPolicy, enforceMealCoverage, places, mask, costContext));
            return;
        }
        for (int index = startIndex; index <= candidates.size() - remaining; index++) {
            selected.add(candidates.get(index));
            buildGroups(
                    candidates, day, placeCountPolicy, enforceMealCoverage, remaining - 1, index + 1,
                    mask | (1L << index), selected, groups, costContext);
            selected.remove(selected.size() - 1);
        }
    }

    private Group group(
            ScheduleDay day,
            PlaceCountPolicy placeCountPolicy,
            boolean enforceMealCoverage,
            List<Place> places,
            long mask,
            DayCostContext costContext
    ) {
        // Visit order is unresolved here, so the coordinate minimum is the available flow estimate.
        long coordinateDistanceCost = costContext.shortestDistance(mask);
        int requiredMeals = MealTimePolicy.requiredMealStops(day, places.size());
        long assignedMeals = Long.bitCount(mask & costContext.mealMask());
        long mealCoverageRisk = enforceMealCoverage
                ? Math.max(0, requiredMeals - assignedMeals) * MEAL_COVERAGE_PENALTY
                : 0;
        if (enforceMealCoverage && requiredMeals > 0) {
            mealCoverageRisk += Math.max(0, assignedMeals - requiredMeals) * EXCESS_MEAL_PENALTY;
        }
        long candidateSuitabilityCost = costContext.preferenceCost(mask);
        long diversityCost = costContext.repetitionCost(mask);
        long routeFlowCost = coordinateDistanceCost / ROUTE_DISTANCE_COST_DIVISOR;
        long activityShortfall = Math.max(0,
                placeCountPolicy.targetActivityMinutes(availableMinutes(day))
                        - estimatedActivityMinutes(places));
        long placeCountCost = placeCountPolicy.placeCountCost(places.size());
        PlanObjective objective = PlanObjectiveEvaluator.evaluate(
                0,
                mealCoverageRisk + activityShortfall,
                0,
                routeFlowCost,
                0,
                candidateSuitabilityCost,
                diversityCost,
                placeCountCost
        );
        long legacyCost = routeFlowCost + mealCoverageRisk + activityShortfall
                + candidateSuitabilityCost + diversityCost + placeCountCost;
        return new Group(
                mask,
                objective,
                candidateSuitabilityCost,
                coordinateDistanceCost,
                legacyCost,
                places);
    }

    private List<Group> beamGroups(
            List<Group> groups,
            int candidateCount,
            long requiredMask,
            long mealMask
    ) {
        List<Group> sorted = groups.stream()
                .sorted(groupComparator())
                .toList();
        Map<Long, Group> selected = new LinkedHashMap<>();
        sorted.stream().limit(MAX_BEAM_GROUPS_PER_DAY)
                .forEach(group -> selected.put(group.mask(), group));
        for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
            long candidateBit = 1L << candidateIndex;
            sorted.stream()
                    .filter(group -> (group.mask() & candidateBit) != 0)
                    .limit(MAX_BEAM_GROUPS_PER_CANDIDATE)
                    .forEach(group -> selected.put(group.mask(), group));
        }
        Map<Long, Integer> mealMaskCounts = new HashMap<>();
        for (Group group : sorted) {
            long groupMealMask = group.mask() & mealMask;
            int count = mealMaskCounts.getOrDefault(groupMealMask, 0);
            if (count >= MAX_BEAM_GROUPS_PER_MEAL_MASK) continue;
            selected.put(group.mask(), group);
            mealMaskCounts.put(groupMealMask, count + 1);
        }
        if (requiredMask != 0) {
            Map<Long, Integer> requiredMaskCounts = new HashMap<>();
            for (Group group : sorted) {
                long groupRequiredMask = group.mask() & requiredMask;
                int count = requiredMaskCounts.getOrDefault(groupRequiredMask, 0);
                if (count >= MAX_BEAM_GROUPS_PER_REQUIRED_MASK) continue;
                selected.put(group.mask(), group);
                requiredMaskCounts.put(groupRequiredMask, count + 1);
            }
        }
        return List.copyOf(selected.values());
    }

    private Map<Long, List<PlanState>> pruneStates(
            Map<Long, List<PlanState>> states,
            long requiredMask,
            long mealMask
    ) {
        List<MaskedPlanState> sorted = states.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(state -> new MaskedPlanState(entry.getKey(), state)))
                .sorted(Comparator.comparing((MaskedPlanState item) -> item.state().objective())
                        .thenComparingLong(item -> item.state().candidateSuitabilityCost())
                        .thenComparingLong(item -> item.state().coordinateDistanceCost())
                        .thenComparingLong(MaskedPlanState::mask)
                        .thenComparing(item -> planKey(item.state().placesByDay())))
                .toList();
        Map<BeamDiversityKey, Integer> countsByDiversity = new HashMap<>();
        Map<Long, List<PlanState>> selected = new TreeMap<>();
        int perRequiredMaskLimit = requiredMask == 0
                ? Math.max(64, MAX_BEAM_STATES / (Long.bitCount(mealMask) + 1))
                : MAX_BEAM_STATES_PER_REQUIRED_MASK;
        int selectedCount = 0;
        for (MaskedPlanState item : sorted) {
            long stateRequiredMask = item.mask() & requiredMask;
            BeamDiversityKey diversityKey = new BeamDiversityKey(
                    stateRequiredMask, Long.bitCount(item.mask() & mealMask));
            int count = countsByDiversity.getOrDefault(diversityKey, 0);
            if (count >= perRequiredMaskLimit) continue;
            List<PlanState> bucket = new ArrayList<>(
                    selected.getOrDefault(item.mask(), List.of()));
            bucket.add(item.state());
            selected.put(item.mask(), List.copyOf(bucket));
            countsByDiversity.put(diversityKey, count + 1);
            selectedCount++;
            if (selectedCount >= MAX_BEAM_STATES) break;
        }
        return selected;
    }

    private static String planKey(List<List<Place>> placesByDay) {
        return placesByDay.stream()
                .map(day -> day.stream()
                        .map(place -> place.getId() == null ? place.getName() : place.getId().toString())
                        .sorted()
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("|"));
    }

    private static Comparator<Group> groupComparator() {
        return Comparator.comparing(Group::objective)
                .thenComparingLong(Group::candidateSuitabilityCost)
                .thenComparingLong(Group::coordinateDistanceCost)
                .thenComparingLong(Group::mask);
    }

    private static Comparator<PlanState> planStateComparator() {
        return Comparator.comparing(PlanState::objective)
                .thenComparingLong(PlanState::candidateSuitabilityCost)
                .thenComparingLong(PlanState::coordinateDistanceCost)
                .thenComparing(plan -> planKey(plan.placesByDay()));
    }

    private static PlanObjective fallbackObjective() {
        return PlanObjectiveEvaluator.evaluate(
                1,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE);
    }

    private long availableMinutes(ScheduleDay day) {
        return java.time.Duration.between(day.getStartTime(), day.getEndTime()).toMinutes();
    }

    private long estimatedActivityMinutes(List<Place> places) {
        return places.stream().mapToLong(VisitDurationPolicy::minutes).sum();
    }

    private ScheduleCreateRequest.Location location(
            String name,
            BigDecimal longitude,
            BigDecimal latitude,
            ScheduleCreateRequest.Location fallback
    ) {
        if (longitude == null || latitude == null) return fallback;
        return new ScheduleCreateRequest.Location(name, longitude, latitude);
    }

    private List<Place> constrainedCandidates(
            List<Place> places,
            Set<Long> requiredPlaceIds,
            int targetPlaceCount,
            ScheduleCreateRequest request
    ) {
        if (!preferenceScorer.lowMobilityProfile(request)) {
            return places;
        }
        List<Place> accepted = new ArrayList<>();
        for (Place place : places) {
            if (requiredPlaceIds.contains(place.getId()) || !preferenceScorer.isMobilityBurden(place)) {
                accepted.add(place);
            }
        }
        return accepted.isEmpty() ? places : List.copyOf(accepted);
    }

    private List<Place> boundedCandidates(List<Place> places, Set<Long> requiredPlaceIds) {
        if (places.size() <= MAX_EXACT_CANDIDATE_POOL) return places;
        List<Place> bounded = new ArrayList<>();
        places.stream()
                .filter(place -> requiredPlaceIds.contains(place.getId()))
                .forEach(bounded::add);
        places.stream()
                .filter(place -> !requiredPlaceIds.contains(place.getId()))
                .limit(Math.max(0, MAX_EXACT_CANDIDATE_POOL - bounded.size()))
                .forEach(bounded::add);
        return List.copyOf(bounded);
    }

    private List<Place> fallbackCandidates(
            List<Place> places,
            Set<Long> requiredPlaceIds,
            int targetPlaceCount
    ) {
        if (places.size() <= targetPlaceCount) return places;
        List<Place> selected = new ArrayList<>();
        places.stream()
                .filter(place -> requiredPlaceIds.contains(place.getId()))
                .forEach(selected::add);
        places.stream()
                .filter(place -> !requiredPlaceIds.contains(place.getId()))
                .limit(Math.max(0, targetPlaceCount - selected.size()))
                .forEach(selected::add);
        return List.copyOf(selected);
    }

    private long requiredMask(List<Place> places, Set<Long> requiredPlaceIds) {
        long mask = 0L;
        for (int index = 0; index < places.size(); index++) {
            if (requiredPlaceIds.contains(places.get(index).getId())) {
                mask |= 1L << index;
            }
        }
        return mask;
    }

    private long mealMask(List<Place> places) {
        long mask = 0L;
        for (int index = 0; index < places.size(); index++) {
            if (MealTimePolicy.isMealPlace(places.get(index))) {
                mask |= 1L << index;
            }
        }
        return mask;
    }

    private record Group(
            long mask,
            PlanObjective objective,
            long candidateSuitabilityCost,
            long coordinateDistanceCost,
            long legacyCost,
            List<Place> places
    ) {
    }

    private record PlanState(
            PlanObjective objective,
            long candidateSuitabilityCost,
            long coordinateDistanceCost,
            long legacyCost,
            List<List<Place>> placesByDay
    ) {
    }

    private record MaskedPlanState(long mask, PlanState state) {
    }

    private record BeamDiversityKey(long requiredMask, int mealCount) {
    }

    public record OptimizedPlan(
            long estimatedCost,
            PlanObjective objective,
            long candidateSuitabilityCost,
            long coordinateDistanceCost,
            List<List<Place>> placesByDay
    ) {
        public OptimizedPlan(long estimatedCost, List<List<Place>> placesByDay) {
            this(
                    estimatedCost,
                    PlanObjectiveEvaluator.empty(),
                    estimatedCost,
                    estimatedCost,
                    placesByDay);
        }
    }

    private static final class DayCostContext {

        private final long[] startDistances;
        private final long[] endDistances;
        private final long[][] betweenDistances;
        private final long[] basePreferenceCosts;
        private final long[] themeCosts;
        private final PlaceExperienceClassifier.ExperienceProfile[] experienceProfiles;
        private final boolean hasThemePreference;
        private final long mealMask;
        private final Map<Long, Long> routeCostMemo = new HashMap<>();

        private DayCostContext(
                List<Place> candidates,
                ScheduleCreateRequest.Location start,
                ScheduleCreateRequest.Location end,
                ScheduleCreateRequest request,
                PlacePreferenceScorer scorer
        ) {
            int size = candidates.size();
            this.startDistances = new long[size];
            this.endDistances = new long[size];
            this.betweenDistances = new long[size][size];
            this.basePreferenceCosts = new long[size];
            this.themeCosts = new long[size];
            this.experienceProfiles = new PlaceExperienceClassifier.ExperienceProfile[size];
            this.hasThemePreference = scorer.hasThemePreference(request);
            long meals = 0L;
            for (int index = 0; index < size; index++) {
                Place place = candidates.get(index);
                startDistances[index] = distance(scorer, start, place);
                endDistances[index] = distance(scorer, place, end);
                int themeScore = scorer.themeScore(place, request);
                int totalScore = scorer.score(place, start, end, request).totalScore();
                basePreferenceCosts[index] = (long) (totalScore - themeScore)
                        * PREFERENCE_COST_MULTIPLIER;
                themeCosts[index] = (long) themeScore * PREFERENCE_COST_MULTIPLIER;
                experienceProfiles[index] = PlaceExperienceClassifier.classify(place);
                if (MealTimePolicy.isMealPlace(place)) meals |= 1L << index;
            }
            for (int left = 0; left < size; left++) {
                for (int right = left + 1; right < size; right++) {
                    Place from = candidates.get(left);
                    Place to = candidates.get(right);
                    long distance = scorer.distanceMeters(
                            from.getLongitude(), from.getLatitude(), to.getLongitude(), to.getLatitude());
                    betweenDistances[left][right] = distance;
                    betweenDistances[right][left] = distance;
                }
            }
            this.mealMask = meals;
        }

        private long shortestDistance(long mask) {
            return shortestDistance(-1, mask);
        }

        private long shortestDistance(int currentIndex, long remainingMask) {
            if (remainingMask == 0) {
                return currentIndex < 0 ? 0 : endDistances[currentIndex];
            }
            long memoKey = (remainingMask << 6) | (currentIndex + 1L);
            Long cached = routeCostMemo.get(memoKey);
            if (cached != null) return cached;
            long best = Long.MAX_VALUE;
            for (long bits = remainingMask; bits != 0; bits &= bits - 1) {
                int nextIndex = Long.numberOfTrailingZeros(bits);
                long inbound = currentIndex < 0
                        ? startDistances[nextIndex]
                        : betweenDistances[currentIndex][nextIndex];
                best = Math.min(best, inbound + shortestDistance(
                        nextIndex, remainingMask & ~(1L << nextIndex)));
            }
            routeCostMemo.put(memoKey, best);
            return best;
        }

        private long preferenceCost(long mask) {
            long cost = 0;
            List<Long> matchingThemeCosts = new ArrayList<>();
            for (long bits = mask; bits != 0; bits &= bits - 1) {
                int index = Long.numberOfTrailingZeros(bits);
                cost += basePreferenceCosts[index];
                if (themeCosts[index] < 0) {
                    matchingThemeCosts.add(themeCosts[index]);
                }
            }
            matchingThemeCosts.sort(Long::compareTo);
            int[] rewardPercentages = {100, 50, 15};
            for (int index = 0; index < Math.min(matchingThemeCosts.size(), rewardPercentages.length); index++) {
                cost += matchingThemeCosts.get(index) * rewardPercentages[index] / 100;
            }
            if (hasThemePreference) {
                int placeCount = Long.bitCount(mask);
                int targetMatches = Math.min(2, Math.max(1, (placeCount + 1) / 2));
                cost += (long) Math.max(0, targetMatches - matchingThemeCosts.size())
                        * THEME_UNMET_PENALTY;
            }
            return cost;
        }

        private long repetitionCost(long mask) {
            int[] experienceCounts = new int[PlaceExperienceClassifier.ExperienceType.values().length];
            int[] semanticGroupCounts = new int[PlaceExperienceClassifier.SemanticGroup.values().length];
            for (long bits = mask; bits != 0; bits &= bits - 1) {
                PlaceExperienceClassifier.ExperienceProfile profile =
                        experienceProfiles[Long.numberOfTrailingZeros(bits)];
                if (!ExperienceSequenceEvaluator.isDiversityScored(profile)) continue;
                experienceCounts[profile.type().ordinal()]++;
                semanticGroupCounts[profile.semanticGroup().ordinal()]++;
            }
            long penalty = 0;
            for (int count : experienceCounts) {
                penalty += duplicatePairCount(count) * SAME_EXPERIENCE_PENALTY;
            }
            for (int count : semanticGroupCounts) {
                penalty += duplicatePairCount(count) * SAME_SEMANTIC_GROUP_PENALTY;
            }
            return penalty + similarProfilePenalty(mask);
        }

        private long similarProfilePenalty(long mask) {
            long penalty = 0;
            for (long leftBits = mask; leftBits != 0; leftBits &= leftBits - 1) {
                int leftIndex = Long.numberOfTrailingZeros(leftBits);
                PlaceExperienceClassifier.ExperienceProfile left = experienceProfiles[leftIndex];
                if (!ExperienceSequenceEvaluator.isDiversityScored(left)) continue;
                long followingBits = leftBits & (leftBits - 1);
                for (long rightBits = followingBits; rightBits != 0; rightBits &= rightBits - 1) {
                    PlaceExperienceClassifier.ExperienceProfile right =
                            experienceProfiles[Long.numberOfTrailingZeros(rightBits)];
                    if (!ExperienceSequenceEvaluator.isDiversityScored(right) || left.type() == right.type()) continue;
                    int similarity = PlaceExperienceClassifier.similarityPercent(left, right);
                    if (similarity >= 65) {
                        penalty += (long) similarity * SIMILAR_EXPERIENCE_PROFILE_PENALTY / 100;
                    }
                }
            }
            return penalty;
        }

        private long duplicatePairCount(int count) {
            return (long) count * (count - 1) / 2;
        }

        private long mealMask() {
            return mealMask;
        }

        private static long distance(
                PlacePreferenceScorer scorer,
                ScheduleCreateRequest.Location start,
                Place end
        ) {
            if (start == null || start.longitude() == null || start.latitude() == null) return 0;
            return scorer.distanceMeters(
                    start.longitude(), start.latitude(), end.getLongitude(), end.getLatitude());
        }

        private static long distance(
                PlacePreferenceScorer scorer,
                Place start,
                ScheduleCreateRequest.Location end
        ) {
            if (end == null || end.longitude() == null || end.latitude() == null) return 0;
            return scorer.distanceMeters(
                    start.getLongitude(), start.getLatitude(), end.longitude(), end.latitude());
        }
    }
}
