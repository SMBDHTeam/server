package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannerEvaluationExtractionTest {

    @Test
    void routeFlowEvaluatorKeepsTheLegacyRouteFlowMetricsExactly() {
        ScheduleDay day = day(
                "해운대 출발", "129.1604", "35.1587",
                "광안리 도착", "129.1186", "35.1532");
        List<Place> places = List.of(
                place("HAE_A", "12", "129.1650", "35.1600"),
                place("GWANG", "12", "129.1186", "35.1532"),
                place("HAE_B", "12", "129.1700", "35.1640"));

        RouteFlowEvaluator.Evaluation evaluation = RouteFlowEvaluator.evaluate(day, places);
        DayRouteOptimizer.RouteFlowMetrics legacyMetrics = DayRouteOptimizer.routeFlow(day, places);

        assertThat(legacyMetrics.regionTransitionCount()).isEqualTo(evaluation.regionTransitionCount());
        assertThat(legacyMetrics.regionReentryCount()).isEqualTo(evaluation.regionReentryCount());
        assertThat(legacyMetrics.directionReversalPenalty())
                .isEqualTo(evaluation.directionReversalPenalty());
        assertThat(legacyMetrics.detourRatio()).isEqualTo(evaluation.detourRatio());
        assertThat(legacyMetrics.totalPenalty()).isEqualTo(evaluation.totalPenalty());
    }

    @Test
    void visitScheduleEvaluatorDelegatesToTheExistingTimePolicy() {
        Place nightView = place("황령산 야경 전망대", "12", "129.0800", "35.1500");

        assertThat(VisitScheduleEvaluator.timeSuitabilityPenalty(nightView, LocalTime.of(14, 0)))
                .isEqualTo(VisitTimePolicy.penalty(nightView, LocalTime.of(14, 0)));
        assertThat(VisitScheduleEvaluator.timeSuitabilityPenalty(nightView, LocalTime.of(18, 0)))
                .isEqualTo(VisitTimePolicy.penalty(nightView, LocalTime.of(18, 0)));
    }

    @Test
    void experienceSequenceEvaluatorPreservesAttractionOnlyRepeatPenalty() {
        Place firstBeach = place("광안리해수욕장", "12", "129.1186", "35.1532");
        Place secondBeach = place("송정해변", "12", "129.1990", "35.1786");
        Place meal = place("해운대 돼지국밥", "39", "129.1604", "35.1587");

        assertThat(ExperienceSequenceEvaluator.consecutivePenalty(List.of(firstBeach, secondBeach)))
                .isEqualTo(16);
        assertThat(ExperienceSequenceEvaluator.consecutivePenalty(List.of(firstBeach, meal, secondBeach)))
                .isZero();
    }

    @Test
    void planObjectiveUsesLexicographicPriorityWithoutWeightCompensation() {
        PlanObjective hardViolation = PlanObjectiveEvaluator.evaluate(
                1, 0, 0, 0, 0, 0, 0, 0);
        PlanObjective higherPreferenceCost = PlanObjectiveEvaluator.evaluate(
                0, 0, 0, 0, 0, 10_000, 0, 0);
        PlanObjective higherRouteFlowCost = PlanObjectiveEvaluator.evaluate(
                0, 0, 0, 1, 0, 0, 0, 0);
        PlanObjective lowerRouteFlowCost = PlanObjectiveEvaluator.evaluate(
                0, 0, 0, 0, 10_000, 10_000, 10_000, 10_000);

        assertThat(hardViolation).isGreaterThan(higherPreferenceCost);
        assertThat(higherPreferenceCost).isLessThan(higherRouteFlowCost);
        assertThat(lowerRouteFlowCost).isLessThan(higherRouteFlowCost);
    }

    private ScheduleDay day(
            String startName,
            String startLongitude,
            String startLatitude,
            String endName,
            String endLongitude,
            String endLatitude
    ) {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                startName, new BigDecimal(startLongitude), new BigDecimal(startLatitude),
                endName, new BigDecimal(endLongitude), new BigDecimal(endLatitude),
                "test", "{}"
        );
        return new ScheduleDay(schedule, 1, LocalDate.parse("2026-07-20"));
    }

    private Place place(String name, String contentTypeId, String longitude, String latitude) {
        return new Place(
                "LOCAL_FIXTURE", name, contentTypeId, name, "관광지", "부산",
                new BigDecimal(longitude), new BigDecimal(latitude), null
        );
    }
}
