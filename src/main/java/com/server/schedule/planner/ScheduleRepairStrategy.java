package com.server.schedule.planner;

import java.util.List;

/** Produces a small set of schedule changes after a concrete feasibility failure. */
public interface ScheduleRepairStrategy {

    List<RepairCandidate> repair(ScheduleRepairContext context);
}
