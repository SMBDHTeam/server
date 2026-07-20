package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Last resort: removes the lowest quality optional stop while retaining at least one visit. */
@Component
public class LowUtilityRemovalRepair implements ScheduleRepairStrategy {

    @Override
    public List<RepairCandidate> repair(ScheduleRepairContext context) {
        // Concrete transit and meal waits can make even the policy minimum infeasible.
        // A one-stop day is preferable to returning an itinerary that cannot be completed.
        if (context.failedDayPlaces().size() <= 1) {
            return List.of();
        }
        Place removable = context.failedDayPlaces().stream()
                .filter(place -> !context.isProtected(place))
                .max(Comparator.comparingInt(place -> context.preferenceScorer().score(
                        place, context.startLocation(), context.endLocation(), context.request()).totalScore()))
                .orElse(null);
        if (removable == null) return List.of();
        List<List<Place>> result = NearbyReplacementRepair.copy(context.placesByDay());
        result.get(context.failedDayIndex()).remove(removable);
        return List.of(RepairCandidate.withAssignments(
                RepairCandidate.RepairType.LOW_UTILITY_REMOVAL,
                result.stream().map(List::copyOf).toList()));
    }
}
