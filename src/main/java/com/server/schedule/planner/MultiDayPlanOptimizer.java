package com.server.schedule.planner;

import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MultiDayPlanOptimizer {

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
        List<Integer> minimumStopsByDay = dailyStopTargets.stream()
                .map(target -> Math.min(2, target))
                .toList();
        int minimumPlaceCount = minimumStopsByDay.stream().mapToInt(Integer::intValue).sum();
        List<Place> selected = constrainedCandidates(
                places,
                requiredPlaceIds,
                minimumPlaceCount,
                request
        );
        if (selected.isEmpty() || selected.size() < minimumPlaceCount) {
            return fallbackAllocator.allocate(places, days, dailyStopTargets);
        }

        Assignment best = new Assignment(Long.MAX_VALUE, List.of());
        List<List<Place>> current = emptyAssignments(days.size());
        best = assign(0, selected, days, dailyStopTargets, minimumStopsByDay, current, best);
        return best.placesByDay().isEmpty()
                ? fallbackAllocator.allocate(selected, days, dailyStopTargets)
                : best.placesByDay();
    }

    private List<Place> constrainedCandidates(
            List<Place> places,
            Set<Long> requiredPlaceIds,
            int minimumPlaceCount,
            ScheduleCreateRequest request
    ) {
        if (!preferenceScorer.lowMobilityProfile(request)) {
            return places;
        }
        List<Place> accepted = new ArrayList<>();
        List<Place> deferred = new ArrayList<>();
        for (Place place : places) {
            if (requiredPlaceIds.contains(place.getId()) || !preferenceScorer.isMobilityBurden(place)) {
                accepted.add(place);
            } else {
                deferred.add(place);
            }
        }
        deferred.sort(Comparator.comparingLong(place -> place.getId() == null ? Long.MAX_VALUE : place.getId()));
        while (accepted.size() < minimumPlaceCount && !deferred.isEmpty()) {
            accepted.add(deferred.remove(0));
        }
        return List.copyOf(accepted);
    }

    private Assignment assign(
            int placeIndex,
            List<Place> places,
            List<ScheduleDay> days,
            List<Integer> targets,
            List<Integer> minimumStopsByDay,
            List<List<Place>> current,
            Assignment best
    ) {
        if (placeIndex == places.size()) {
            for (int dayIndex = 0; dayIndex < current.size(); dayIndex++) {
                if (current.get(dayIndex).size() < minimumStopsByDay.get(dayIndex)) return best;
            }
            long cost = assignmentCost(current, days);
            return cost < best.cost() ? new Assignment(cost, immutableAssignments(current)) : best;
        }
        Place place = places.get(placeIndex);
        Assignment result = best;
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            if (current.get(dayIndex).size() >= targets.get(dayIndex)) continue;
            current.get(dayIndex).add(place);
            result = assign(
                    placeIndex + 1,
                    places,
                    days,
                    targets,
                    minimumStopsByDay,
                    current,
                    result
            );
            current.get(dayIndex).remove(current.get(dayIndex).size() - 1);
        }
        return result;
    }

    private long assignmentCost(List<List<Place>> placesByDay, List<ScheduleDay> days) {
        long cost = 0;
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            cost += shortestDistance(days.get(dayIndex), placesByDay.get(dayIndex));
        }
        return cost;
    }

    private long shortestDistance(ScheduleDay day, List<Place> places) {
        return shortestDistance(
                day.getStartLongitude(),
                day.getStartLatitude(),
                day.getEndLongitude(),
                day.getEndLatitude(),
                places,
                new HashSet<>()
        );
    }

    private long shortestDistance(
            BigDecimal currentLongitude,
            BigDecimal currentLatitude,
            BigDecimal endLongitude,
            BigDecimal endLatitude,
            List<Place> places,
            Set<Integer> visited
    ) {
        if (visited.size() == places.size()) {
            return preferenceScorer.distanceMeters(
                    currentLongitude, currentLatitude, endLongitude, endLatitude);
        }
        long best = Long.MAX_VALUE;
        for (int index = 0; index < places.size(); index++) {
            if (!visited.add(index)) continue;
            Place place = places.get(index);
            long cost = preferenceScorer.distanceMeters(
                    currentLongitude,
                    currentLatitude,
                    place.getLongitude(),
                    place.getLatitude()
            ) + shortestDistance(
                    place.getLongitude(),
                    place.getLatitude(),
                    endLongitude,
                    endLatitude,
                    places,
                    visited
            );
            visited.remove(index);
            best = Math.min(best, cost);
        }
        return best;
    }

    private List<List<Place>> emptyAssignments(int dayCount) {
        List<List<Place>> assignments = new ArrayList<>();
        for (int index = 0; index < dayCount; index++) assignments.add(new ArrayList<>());
        return assignments;
    }

    private List<List<Place>> immutableAssignments(List<List<Place>> assignments) {
        return assignments.stream().map(List::copyOf).toList();
    }

    private record Assignment(long cost, List<List<Place>> placesByDay) {
    }
}
