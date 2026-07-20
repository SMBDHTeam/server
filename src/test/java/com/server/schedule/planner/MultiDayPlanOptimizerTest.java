package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MultiDayPlanOptimizerTest {

    private final MultiDayPlanOptimizer optimizer = new MultiDayPlanOptimizer(
            new DayPlaceAllocator(),
            new PlacePreferenceScorer()
    );

    @Test
    void excludesOptionalBurdenPlacesForLowMobilityTrip() {
        List<ScheduleDay> days = days();
        Place west = place(1L, "국제시장", "129.0286", "35.1025");
        Place east = place(2L, "해운대해수욕장", "129.1604", "35.1587");
        Place burden = place(3L, "달맞이길 전망대", "129.1775", "35.1578");

        List<List<Place>> result = optimizer.optimize(
                List.of(west, east, burden),
                Set.of(),
                days,
                List.of(1, 1),
                request()
        );

        assertThat(result).allMatch(day -> !day.isEmpty());
        assertThat(result).flatExtracting(day -> day)
                .extracting(Place::getName)
                .containsExactlyInAnyOrder("국제시장", "해운대해수욕장")
                .doesNotContain("달맞이길 전망대");
    }

    @Test
    void retainsRequiredBurdenPlace() {
        List<ScheduleDay> days = days();
        Place west = place(1L, "국제시장", "129.0286", "35.1025");
        Place burden = place(3L, "감천문화마을", "129.0106", "35.0974");

        List<List<Place>> result = optimizer.optimize(
                List.of(west, burden),
                Set.of(3L),
                days,
                List.of(1, 1),
                request()
        );

        assertThat(result).flatExtracting(day -> day)
                .extracting(Place::getId)
                .contains(3L);
    }

    @Test
    void prioritizesCompactRouteBeforeSoftThemePreference() {
        ScheduleDay day = days().get(0);
        Place nearbyMismatch = place(1L, "부산역 쇼핑몰", "129.0410", "35.1160");
        Place preferredSea = place(2L, "광안리해수욕장", "129.1186", "35.1532");
        Place optionalPark = place(3L, "부산시민공원", "129.0595", "35.1667");
        ScheduleCreateRequest request = request(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_NORMAL"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_SEA")
        ));

        List<List<Place>> result = optimizer.optimize(
                List.of(nearbyMismatch, preferredSea, optionalPark),
                Set.of(),
                List.of(day),
                List.of(1),
                request
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).singleElement()
                .extracting(Place::getId)
                .isEqualTo(1L);
        assertThat(optimizer.ranked(
                List.of(nearbyMismatch, preferredSea, optionalPark),
                Set.of(), List.of(day), List.of(1), request, 1).get(0)
                .objective().preferenceCost()).isPositive();
    }

    @Test
    void satisfiesThemeWithoutSelectingOnlyTheSameExperience() {
        ScheduleDay day = days().get(0);
        Place firstVillage = place(1L, "감천문화마을", "12", "129.0106", "35.0974");
        Place secondVillage = place(2L, "흰여울문화마을", "12", "129.0440", "35.0780");
        Place thirdVillage = place(3L, "색채마을 골목", "12", "129.0240", "35.0910");
        Place park = place(4L, "용두산공원", "12", "129.0324", "35.1019");
        Place museum = place(5L, "부산근현대역사관", "14", "129.0300", "35.1040");
        Place activity = place(6L, "송도 케이블카", "28", "129.0170", "35.0770");
        ScheduleCreateRequest request = request(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_NORMAL"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_LOCAL")
        ));

        List<Place> selected = optimizer.optimize(
                List.of(firstVillage, secondVillage, thirdVillage, park, museum, activity),
                Set.of(), List.of(day), List.of(3), request).get(0);

        long villageWalkCount = selected.stream()
                .map(PlaceExperienceClassifier::classify)
                .filter(profile -> profile.type()
                        == PlaceExperienceClassifier.ExperienceType.VILLAGE_STREET_WALK)
                .count();
        assertThat(villageWalkCount).isBetween(1L, 2L);
        assertThat(selected).hasSize(3);
    }

    @Test
    void keepsThemeMatchesWithoutForcingEveryDayIntoTheSameExperience() {
        List<Place> candidates = List.of(
                place(1L, "광안리해수욕장", "12", "129.0200", "35.1000"),
                place(2L, "송정해변", "12", "129.0300", "35.1100"),
                place(3L, "다대포해수욕장", "12", "129.0400", "35.1200"),
                place(4L, "일광해변", "12", "129.0500", "35.1300"),
                place(5L, "부산박물관", "14", "129.0600", "35.1400"),
                place(6L, "부산시민공원", "12", "129.0700", "35.1500")
        );
        ScheduleCreateRequest request = request(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_SEA")
        ));

        List<List<Place>> result = optimizer.optimize(
                candidates, Set.of(), days(), List.of(2, 2), request);

        long beachCount = result.stream().flatMap(List::stream)
                .map(PlaceExperienceClassifier::classify)
                .filter(profile -> profile.type()
                        == PlaceExperienceClassifier.ExperienceType.BEACH_WALK)
                .count();
        assertThat(beachCount).isPositive();
        assertThat(beachCount).isLessThan(4L);
        assertThat(result).flatExtracting(day -> day)
                .extracting(Place::getId)
                .doesNotHaveDuplicates();
    }

    @Test
    void rankedPreservesAlternativeDateAssignmentsForActualRouteReranking() {
        List<Place> candidates = List.of(
                place(1L, "남포 A", "129.0286", "35.1025"),
                place(2L, "남포 B", "129.0350", "35.1060"),
                place(3L, "해운대 A", "129.1604", "35.1587"),
                place(4L, "해운대 B", "129.1700", "35.1640")
        );

        List<MultiDayPlanOptimizer.OptimizedPlan> plans = optimizer.ranked(
                candidates, Set.of(), days(), List.of(2, 2), request(), 3);

        assertThat(plans).hasSize(3);
        assertThat(plans).extracting(MultiDayPlanOptimizer.OptimizedPlan::objective)
                .isSorted();
        assertThat(plans.stream().map(plan -> plan.placesByDay().stream()
                        .map(day -> day.stream().map(Place::getId).sorted()
                                .map(String::valueOf).collect(Collectors.joining(",")))
                        .collect(Collectors.joining("|"))))
                .doesNotHaveDuplicates();
        assertThat(optimizer.optimize(
                candidates, Set.of(), days(), List.of(2, 2), request()))
                .isEqualTo(plans.get(0).placesByDay());
    }

    @Test
    void largePlanDistributesMealStopsAcrossEveryDay() {
        List<ScheduleDay> days = threeDays();
        List<Place> places = List.of(
                place(1L, "광안리해수욕장", "12", "129.1186", "35.1532"),
                place(2L, "해운대해수욕장", "12", "129.1604", "35.1587"),
                place(3L, "송정해수욕장", "12", "129.1990", "35.1786"),
                place(4L, "부산박물관", "14", "129.0928", "35.1296"),
                place(5L, "국제시장", "38", "129.0286", "35.1025"),
                place(6L, "송도해수욕장", "12", "129.0176", "35.0763"),
                place(7L, "낙동강하구", "12", "128.9460", "35.1045"),
                place(8L, "부산시민공원", "12", "129.0595", "35.1667"),
                place(9L, "용두산공원", "12", "129.0324", "35.1019"),
                place(10L, "해운대 점심 식당", "39", "129.1580", "35.1600"),
                place(11L, "광안리 저녁 식당", "39", "129.1200", "35.1540"),
                place(12L, "남포 점심 식당", "39", "129.0310", "35.1010"),
                place(13L, "송도 저녁 식당", "39", "129.0200", "35.0780"),
                place(14L, "사상 점심 식당", "39", "128.9900", "35.1630"),
                place(15L, "공항 저녁 식당", "39", "128.9500", "35.1700")
        );
        ScheduleCreateRequest request = request(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_NORMAL"),
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_PACKED"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_FOOD")
        ));

        List<List<Place>> result = optimizer.optimize(
                places, Set.of(), days, List.of(5, 5, 5), request);

        assertThat(result).hasSize(3).allSatisfy(day -> {
            assertThat(day).hasSize(5);
            assertThat(day.stream().filter(MealTimePolicy::isMealPlace)).hasSize(2);
        });
        assertThat(result).flatExtracting(day -> day)
                .extracting(Place::getId)
                .doesNotHaveDuplicates();
    }

    @Test
    void largePlanWithSurplusCandidatesPreservesMealCoverage() {
        List<Place> candidates = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            candidates.add(place(
                    (long) index + 1,
                    "활동 장소 " + index,
                    "12",
                    decimal("129.0100").add(decimal("0.008").multiply(BigDecimal.valueOf(index))).toPlainString(),
                    decimal("35.0800").add(decimal("0.004").multiply(BigDecimal.valueOf(index))).toPlainString()
            ));
        }
        for (int index = 0; index < 5; index++) {
            candidates.add(place(
                    (long) index + 15,
                    "식사 장소 " + index,
                    "39",
                    decimal("129.0250").add(decimal("0.020").multiply(BigDecimal.valueOf(index))).toPlainString(),
                    decimal("35.0950").add(decimal("0.010").multiply(BigDecimal.valueOf(index))).toPlainString()
            ));
        }
        ScheduleCreateRequest request = request(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_PACKED"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_FOOD")
        ));

        List<List<Place>> result = optimizer.optimize(
                candidates, Set.of(), threeDaysWithShortFinalDay(), List.of(5, 5, 5), request);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(day -> day.stream().filter(MealTimePolicy::isMealPlace).count())
                .containsExactly(2L, 2L, 1L);
        assertThat(result).allSatisfy(day -> assertThat(day).hasSize(5));
    }

    @Test
    void rankedLargePlanKeepsTheBeamWithinThePlannerTimeBudget() {
        List<Place> candidates = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            candidates.add(place(
                    (long) index + 1,
                    "활동 장소 " + index,
                    "12",
                    decimal("129.0100").add(decimal("0.008").multiply(BigDecimal.valueOf(index))).toPlainString(),
                    decimal("35.0800").add(decimal("0.004").multiply(BigDecimal.valueOf(index))).toPlainString()
            ));
        }
        for (int index = 0; index < 5; index++) {
            candidates.add(place(
                    (long) index + 15,
                    "식사 장소 " + index,
                    "39",
                    decimal("129.0250").add(decimal("0.020").multiply(BigDecimal.valueOf(index))).toPlainString(),
                    decimal("35.0950").add(decimal("0.010").multiply(BigDecimal.valueOf(index))).toPlainString()
            ));
        }

        List<MultiDayPlanOptimizer.OptimizedPlan> result = assertTimeout(
                Duration.ofSeconds(10),
                () -> optimizer.ranked(
                        candidates, Set.of(), threeDaysWithShortFinalDay(),
                        List.of(5, 5, 3), request(), 3)
        );

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(plan -> {
            assertThat(plan.placesByDay()).extracting(List::size)
                    .containsExactly(5, 5, 3);
            assertThat(plan.placesByDay())
                    .extracting(day -> day.stream().filter(MealTimePolicy::isMealPlace).count())
                    .containsExactly(2L, 2L, 1L);
        });
    }

    private List<ScheduleDay> days() {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-21"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                "부산역", decimal("129.0403"), decimal("35.1151"),
                "김해국제공항", decimal("128.9485"), decimal("35.1732"),
                "test", "{}"
        );
        return List.of(
                new ScheduleDay(
                        schedule, 1, LocalDate.parse("2026-07-20"),
                        LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                        "부산역", decimal("129.0403"), decimal("35.1151"),
                        "해운대 숙소", decimal("129.1580"), decimal("35.1590")
                ),
                new ScheduleDay(
                        schedule, 2, LocalDate.parse("2026-07-21"),
                        LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                        "해운대 숙소", decimal("129.1580"), decimal("35.1590"),
                        "김해국제공항", decimal("128.9485"), decimal("35.1732")
                )
        );
    }

    private List<ScheduleDay> threeDays() {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-22"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                "부산역", decimal("129.0403"), decimal("35.1151"),
                "김해국제공항", decimal("128.9485"), decimal("35.1732"),
                "test", "{}"
        );
        return List.of(
                new ScheduleDay(
                        schedule, 1, LocalDate.parse("2026-07-20"),
                        LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                        "부산역", decimal("129.0403"), decimal("35.1151"),
                        "해운대 숙소", decimal("129.1580"), decimal("35.1590")),
                new ScheduleDay(
                        schedule, 2, LocalDate.parse("2026-07-21"),
                        LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                        "해운대 숙소", decimal("129.1580"), decimal("35.1590"),
                        "남포동 숙소", decimal("129.0320"), decimal("35.1000")),
                new ScheduleDay(
                        schedule, 3, LocalDate.parse("2026-07-22"),
                        LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                        "남포동 숙소", decimal("129.0320"), decimal("35.1000"),
                        "김해국제공항", decimal("128.9485"), decimal("35.1732"))
        );
    }

    private List<ScheduleDay> threeDaysWithShortFinalDay() {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-22"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                "부산역", decimal("129.0403"), decimal("35.1151"),
                "김해국제공항", decimal("128.9485"), decimal("35.1732"),
                "test", "{}"
        );
        return List.of(
                new ScheduleDay(
                        schedule, 1, LocalDate.parse("2026-07-20"),
                        LocalTime.parse("09:00"), LocalTime.parse("20:00"),
                        "부산역", decimal("129.0403"), decimal("35.1151"),
                        "해운대 숙소", decimal("129.1580"), decimal("35.1590")),
                new ScheduleDay(
                        schedule, 2, LocalDate.parse("2026-07-21"),
                        LocalTime.parse("10:00"), LocalTime.parse("20:00"),
                        "해운대 숙소", decimal("129.1580"), decimal("35.1590"),
                        "남포동 숙소", decimal("129.0320"), decimal("35.1000")),
                new ScheduleDay(
                        schedule, 3, LocalDate.parse("2026-07-22"),
                        LocalTime.parse("10:00"), LocalTime.parse("16:30"),
                        "남포동 숙소", decimal("129.0320"), decimal("35.1000"),
                        "김해국제공항", decimal("128.9485"), decimal("35.1732"))
        );
    }

    private ScheduleCreateRequest request() {
        return request(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_LOW_WALK")
        ));
    }

    private ScheduleCreateRequest request(List<ScheduleCreateRequest.SelectedAnswer> answers) {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-21"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                location("부산역", "129.0403", "35.1151"),
                location("김해국제공항", "128.9485", "35.1732"),
                answers,
                List.of()
        );
    }

    private ScheduleCreateRequest.Location location(String name, String longitude, String latitude) {
        return new ScheduleCreateRequest.Location(name, decimal(longitude), decimal(latitude));
    }

    private Place place(Long id, String name, String longitude, String latitude) {
        return place(id, name, "12", longitude, latitude);
    }

    private Place place(Long id, String name, String contentTypeId, String longitude, String latitude) {
        Place place = new Place(
                "LOCAL_FIXTURE", name, contentTypeId, name, "관광지", "부산",
                decimal(longitude), decimal(latitude), null
        );
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
