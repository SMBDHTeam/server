package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Replaces a low-utility optional stop with an unused candidate that is better for the day endpoints. */
@Component
public class NearbyReplacementRepair implements ScheduleRepairStrategy {

    private static final int MAX_CANDIDATES = 4;

    @Override
    public List<RepairCandidate> repair(ScheduleRepairContext context) {
        Set<Long> assignedIds = new HashSet<>();
        context.placesByDay().forEach(day -> day.forEach(place -> assignedIds.add(place.getId())));
        List<Place> removable = context.failedDayPlaces().stream()
                .filter(place -> !context.isProtected(place))
                .sorted(Comparator.comparingInt((Place place) -> score(context, place)).reversed())
                .toList();
        List<Place> replacements = context.allCandidates().stream()
                .filter(place -> !assignedIds.contains(place.getId()))
                .sorted(Comparator.comparingInt(place -> score(context, place)))
                .toList();
        List<RepairCandidate> candidates = new ArrayList<>();
        for (Place removed : removable) {
            int removedScore = score(context, removed);
            for (Place replacement : replacements) {
                if (MealTimePolicy.isMealPlace(replacement) != MealTimePolicy.isMealPlace(removed)) continue;
                if (score(context, replacement) >= removedScore) continue;
                candidates.add(RepairCandidate.withAssignments(
                        RepairCandidate.RepairType.NEARBY_REPLACEMENT,
                        replace(context.placesByDay(), context.failedDayIndex(), removed, replacement)));
                if (candidates.size() >= MAX_CANDIDATES) return List.copyOf(candidates);
            }
        }
        return List.copyOf(candidates);
    }

    private int score(ScheduleRepairContext context, Place place) {
        return context.preferenceScorer().score(
                place, context.startLocation(), context.endLocation(), context.request()).totalScore();
    }

    private List<List<Place>> replace(
            List<List<Place>> placesByDay,
            int dayIndex,
            Place removed,
            Place replacement
    ) {
        List<List<Place>> result = copy(placesByDay);
        int index = result.get(dayIndex).indexOf(removed);
        result.get(dayIndex).set(index, replacement);
        return result.stream().map(List::copyOf).toList();
    }

    static List<List<Place>> copy(List<List<Place>> placesByDay) {
        return placesByDay.stream().map(ArrayList::new).<List<Place>>map(list -> list).toList();
    }
}
