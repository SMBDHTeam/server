package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DayRouteOptimizerTest {

    private final DayRouteOptimizer optimizer = new DayRouteOptimizer();

    @Test
    void selectsTheLowestCostVisitOrder() {
        ScheduleDay day = day();
        Place placeA = place("A", "129.0800", "35.1500");
        Place placeB = place("B", "129.1200", "35.1800");
        Map<String, Integer> costs = Map.of(
                "START>A", 50,
                "START>B", 10,
                "A>B", 50,
                "B>A", 10,
                "A>END", 10,
                "B>END", 50
        );

        DayRouteOptimizer.OptimizedDayRoute result = optimizer.optimize(
                day,
                List.of(placeA, placeB),
                (origin, destination) -> route(costs.get(origin.name() + ">" + destination.name())),
                new DayRouteOptimizer.OptimizationPreference(0, 0)
        );

        assertThat(result.places()).extracting(Place::getName).containsExactly("B", "A");
        assertThat(result.totalMinutes()).isEqualTo(30);
    }

    private ScheduleDay day() {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                "START",
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                "END",
                new BigDecimal("128.9485"),
                new BigDecimal("35.1732"),
                "test",
                "{}"
        );
        return new ScheduleDay(schedule, 1, LocalDate.parse("2026-07-20"));
    }

    private Place place(String name, String longitude, String latitude) {
        return new Place(
                "TOUR_API",
                name,
                "12",
                name,
                "관광지",
                "부산",
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                null
        );
    }

    private TransitRouteResult route(int minutes) {
        return new TransitRouteResult(minutes, null, List.of(), List.of(), "{}");
    }
}
