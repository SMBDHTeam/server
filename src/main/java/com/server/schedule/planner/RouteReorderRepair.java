package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Uses 2-opt order variants before changing the selected places. */
@Component
public class RouteReorderRepair implements ScheduleRepairStrategy {

    private static final int MAX_CANDIDATES = 6;

    @Override
    public List<RepairCandidate> repair(ScheduleRepairContext context) {
        if (context.currentOrder().size() < 3
                || context.currentOrder().stream().anyMatch(context::isFixedEvent)) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<RepairCandidate> candidates = new ArrayList<>();
        List<Place> order = context.currentOrder();
        for (int from = 0; from < order.size() - 1 && candidates.size() < MAX_CANDIDATES; from++) {
            for (int to = from + 1; to < order.size() && candidates.size() < MAX_CANDIDATES; to++) {
                List<Place> variant = new ArrayList<>(order);
                java.util.Collections.reverse(variant.subList(from, to + 1));
                String key = variant.stream().map(place -> String.valueOf(place.getId()))
                        .collect(java.util.stream.Collectors.joining(">"));
                if (seen.add(key)) {
                    candidates.add(RepairCandidate.withOrder(
                            context.placesByDay(), context.failedDayIndex(), variant));
                }
            }
        }
        return List.copyOf(candidates);
    }
}
