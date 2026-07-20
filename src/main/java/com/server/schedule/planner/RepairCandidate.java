package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.util.List;
import java.util.Map;

/** Immutable repair proposal. Route order overrides apply only to the specified day. */
public record RepairCandidate(
        RepairType type,
        List<List<Place>> placesByDay,
        Map<Integer, List<Place>> orderOverrides,
        boolean reduceToMinimumStay
) {

    public RepairCandidate {
        placesByDay = placesByDay.stream().map(List::copyOf).toList();
        orderOverrides = Map.copyOf(orderOverrides);
    }

    public static RepairCandidate withAssignments(RepairType type, List<List<Place>> placesByDay) {
        return new RepairCandidate(type, placesByDay, Map.of(), false);
    }

    public static RepairCandidate withOrder(
            List<List<Place>> placesByDay,
            int dayIndex,
            List<Place> order
    ) {
        return new RepairCandidate(RepairType.ROUTE_REORDER, placesByDay, Map.of(dayIndex, List.copyOf(order)), false);
    }

    public static RepairCandidate withShorterStays(List<List<Place>> placesByDay) {
        return new RepairCandidate(RepairType.STAY_DURATION, placesByDay, Map.of(), true);
    }

    public enum RepairType {
        ROUTE_REORDER,
        STAY_DURATION,
        NEARBY_REPLACEMENT,
        CROSS_DAY_MOVE,
        LOW_UTILITY_REMOVAL
    }
}
