package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Moves an optional place away from an overrun day when another day has capacity. */
@Component
public class CrossDayMoveRepair implements ScheduleRepairStrategy {

    @Override
    public List<RepairCandidate> repair(ScheduleRepairContext context) {
        if (context.failedDayPlaces().size()
                <= context.placeCountPolicies().get(context.failedDayIndex()).absoluteMinimum()) {
            return List.of();
        }
        List<Place> movable = context.failedDayPlaces().stream()
                .filter(place -> !context.isProtected(place))
                .sorted(Comparator.<Place>comparingInt(place -> context.preferenceScorer().score(
                        place, context.startLocation(), context.endLocation(), context.request()).totalScore()).reversed())
                .toList();
        for (int targetDayIndex = 0; targetDayIndex < context.placesByDay().size(); targetDayIndex++) {
            if (targetDayIndex == context.failedDayIndex()
                    || context.placesByDay().get(targetDayIndex).size()
                    >= context.placeCountPolicies().get(targetDayIndex).maximum()) {
                continue;
            }
            for (Place place : movable) {
                List<List<Place>> result = NearbyReplacementRepair.copy(context.placesByDay());
                result.get(context.failedDayIndex()).remove(place);
                result.get(targetDayIndex).add(place);
                return List.of(RepairCandidate.withAssignments(
                        RepairCandidate.RepairType.CROSS_DAY_MOVE,
                        result.stream().map(List::copyOf).toList()));
            }
        }
        return List.of();
    }
}
