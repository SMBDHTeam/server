package com.server.schedule.planner;

import java.util.List;
import org.springframework.stereotype.Component;

/** Applies repair strategies in a fixed quality-preserving order. */
@Component
public class ScheduleRepairEngine {

    private final List<ScheduleRepairStrategy> strategies;

    public ScheduleRepairEngine(
            RouteReorderRepair routeReorderRepair,
            StayDurationRepair stayDurationRepair,
            NearbyReplacementRepair nearbyReplacementRepair,
            CrossDayMoveRepair crossDayMoveRepair,
            LowUtilityRemovalRepair lowUtilityRemovalRepair
    ) {
        this.strategies = List.of(
                routeReorderRepair,
                stayDurationRepair,
                nearbyReplacementRepair,
                crossDayMoveRepair,
                lowUtilityRemovalRepair
        );
    }

    public List<ScheduleRepairStrategy> strategies() {
        return strategies;
    }
}
