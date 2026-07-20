package com.server.schedule.planner;

import java.util.List;
import org.springframework.stereotype.Component;

/** Requests the shared feasibility checker to reduce non-fixed stays to the minimum allowed duration. */
@Component
public class StayDurationRepair implements ScheduleRepairStrategy {

    @Override
    public List<RepairCandidate> repair(ScheduleRepairContext context) {
        return List.of(RepairCandidate.withShorterStays(context.placesByDay()));
    }
}
