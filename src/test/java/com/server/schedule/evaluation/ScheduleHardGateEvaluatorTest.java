package com.server.schedule.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleHardGateEvaluatorTest {

    private final ScheduleHardGateEvaluator evaluator = new ScheduleHardGateEvaluator();

    @Test
    void rejectsEmptyDayAndMissingMustVisitPlace() {
        Schedule schedule = schedule();
        new ScheduleDay(schedule, 1, LocalDate.parse("2026-07-20"));

        ScheduleHardGateResult result = evaluator.evaluate(schedule, Set.of(101L));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).containsExactly(
                "MUST_VISIT_PLACE_MISSING",
                "EMPTY_DAY:1"
        );
    }

    private Schedule schedule() {
        return new Schedule(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                "부산역",
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                "부산역",
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                "test",
                "{}"
        );
    }
}
