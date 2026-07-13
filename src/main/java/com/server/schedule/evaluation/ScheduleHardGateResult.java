package com.server.schedule.evaluation;

import java.util.List;

public record ScheduleHardGateResult(
        boolean passed,
        List<String> violations
) {
}
