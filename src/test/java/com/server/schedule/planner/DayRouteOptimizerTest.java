package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Test
    void avoidsASingleLongTransitWhenTotalMinutesAreEqual() {
        ScheduleDay day = day();
        Place placeA = place("A", "129.0800", "35.1500");
        Place placeB = place("B", "129.1200", "35.1800");
        Map<String, Integer> costs = Map.of(
                "START>A", 80,
                "A>B", 10,
                "B>END", 10,
                "START>B", 40,
                "B>A", 30,
                "A>END", 30
        );

        DayRouteOptimizer.OptimizedDayRoute result = optimizer.optimize(
                day,
                List.of(placeA, placeB),
                (origin, destination) -> route(costs.get(origin.name() + ">" + destination.name())),
                new DayRouteOptimizer.OptimizationPreference(0, 0)
        );

        assertThat(result.places()).extracting(Place::getName).containsExactly("B", "A");
        assertThat(result.totalMinutes()).isEqualTo(100);
        assertThat(result.optimizationCost())
                .isEqualTo(result.totalMinutes() + result.routeFlow().totalPenalty());
    }

    @Test
    void keepsTheOnlyMealInsideTheActiveLunchWindow() {
        ScheduleDay day = day(LocalTime.parse("13:00"), LocalTime.parse("16:30"));
        Place attraction = place("ATTRACTION", "12", "129.0800", "35.1500");
        Place meal = place("MEAL", "39", "129.1200", "35.1800");
        Map<String, Integer> costs = Map.of(
                "START>ATTRACTION", 10,
                "ATTRACTION>MEAL", 30,
                "MEAL>END", 10,
                "START>MEAL", 30,
                "MEAL>ATTRACTION", 30,
                "ATTRACTION>END", 30
        );

        DayRouteOptimizer.OptimizedDayRoute result = optimizer.optimize(
                day,
                List.of(attraction, meal),
                (origin, destination) -> route(costs.get(origin.name() + ">" + destination.name())),
                new DayRouteOptimizer.OptimizationPreference(0, 0),
                ignored -> 60
        );

        assertThat(result.places()).extracting(Place::getName)
                .containsExactly("MEAL", "ATTRACTION");
    }

    @Test
    void preservesAnActualRouteCandidateForEverySingleMealPosition() {
        ScheduleDay day = day(LocalTime.parse("10:00"), LocalTime.parse("16:30"));
        Place first = place("A", "12", "129.0600", "35.1300");
        Place second = place("B", "12", "129.0800", "35.1500");
        Place third = place("C", "12", "129.1000", "35.1700");
        Place meal = place("MEAL", "39", "129.1200", "35.1800");

        List<DayRouteOptimizer.OptimizedDayRoute> candidates = optimizer
                .rankedWithMealPositionDiversity(
                        day,
                        List.of(first, second, third, meal),
                        (origin, destination) -> route(10),
                        new DayRouteOptimizer.OptimizationPreference(0, 0),
                        ignored -> 60,
                        3
                );

        Set<Integer> mealPositions = candidates.stream()
                .map(candidate -> candidate.places().indexOf(meal))
                .collect(Collectors.toSet());
        assertThat(mealPositions).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void keepsACompactOrderWhenExperienceDiversityWouldCauseARegionReentry() {
        ScheduleDay day = day();
        Place firstBeach = place("광안리해수욕장", "12", "129.1186", "35.1532");
        Place secondBeach = place("송정해변", "12", "129.1990", "35.1786");
        Place museum = place("부산박물관", "14", "129.0928", "35.1296");

        DayRouteOptimizer.OptimizedDayRoute result = optimizer.optimize(
                day,
                List.of(firstBeach, secondBeach, museum),
                (origin, destination) -> route(10),
                new DayRouteOptimizer.OptimizationPreference(0, 0)
        );

        assertThat(result.routeFlow().regionReentryCount()).isZero();
    }

    @Test
    void avoidsAReenteredRegionEvenWhenItsRawTransitTimeIsSlightlyLower() {
        ScheduleDay day = day(
                "해운대 출발", "129.1604", "35.1587",
                "광안리 도착", "129.1186", "35.1532");
        Place haeundaeFirst = place("HAE_A", "12", "129.1650", "35.1600");
        Place haeundaeSecond = place("HAE_B", "12", "129.1700", "35.1640");
        Place gwangalli = place("GWANG", "12", "129.1186", "35.1532");
        Map<String, Integer> costs = new HashMap<>();
        costs.put("해운대 출발>HAE_A", 10);
        costs.put("HAE_A>HAE_B", 10);
        costs.put("HAE_B>GWANG", 10);
        costs.put("GWANG>광안리 도착", 10);
        costs.put("HAE_A>GWANG", 5);
        costs.put("GWANG>HAE_B", 5);
        costs.put("HAE_B>광안리 도착", 5);
        for (String origin : List.of("해운대 출발", "HAE_A", "HAE_B", "GWANG")) {
            for (String destination : List.of("HAE_A", "HAE_B", "GWANG", "광안리 도착")) {
                if (!origin.equals(destination)) {
                    costs.putIfAbsent(origin + ">" + destination, 50);
                }
            }
        }

        DayRouteOptimizer.OptimizedDayRoute result = optimizer.optimize(
                day,
                List.of(haeundaeFirst, haeundaeSecond, gwangalli),
                (origin, destination) -> route(costs.get(origin.name() + ">" + destination.name())),
                new DayRouteOptimizer.OptimizationPreference(0, 0)
        );

        assertThat(result.places()).extracting(Place::getName)
                .containsExactly("HAE_A", "HAE_B", "GWANG");
        assertThat(result.routeFlow().regionTransitionCount()).isEqualTo(1);
        assertThat(result.routeFlow().regionReentryCount()).isZero();
    }

    private ScheduleDay day() {
        return day(LocalTime.parse("09:00"), LocalTime.parse("19:00"));
    }

    private ScheduleDay day(LocalTime startTime, LocalTime endTime) {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-20"),
                startTime,
                endTime,
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

    private Place place(String name, String longitude, String latitude) {
        return place(name, "12", longitude, latitude);
    }

    private Place place(String name, String contentTypeId, String longitude, String latitude) {
        return new Place(
                "TOUR_API",
                name,
                contentTypeId,
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
