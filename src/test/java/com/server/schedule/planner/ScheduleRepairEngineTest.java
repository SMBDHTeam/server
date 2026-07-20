package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleRepairEngineTest {

    private final PlacePreferenceScorer scorer = new PlacePreferenceScorer();

    @Test
    void exposesRepairStrategiesInQualityPreservingOrder() {
        ScheduleRepairEngine engine = engine();

        assertThat(engine.strategies()).extracting(strategy -> strategy.getClass().getSimpleName())
                .containsExactly(
                        "RouteReorderRepair",
                        "StayDurationRepair",
                        "NearbyReplacementRepair",
                        "CrossDayMoveRepair",
                        "LowUtilityRemovalRepair");
    }

    @Test
    void replacesAnOptionalDistantPlaceWithAnUnusedNearbyPlace() {
        Place distant = place(1L, "기장 먼 장소", "129.2150", "35.2440");
        Place nearby = place(2L, "부산역 근처", "129.0450", "35.1180");
        ScheduleRepairContext context = context(
                List.of(List.of(distant)), List.of(distant, nearby), Set.of(), Set.of());

        RepairCandidate candidate = new NearbyReplacementRepair().repair(context).get(0);

        assertThat(candidate.type()).isEqualTo(RepairCandidate.RepairType.NEARBY_REPLACEMENT);
        assertThat(candidate.placesByDay().get(0)).containsExactly(nearby);
    }

    @Test
    void keepsMealRoleWhenReplacingAnOptionalPlace() {
        Place distantMeal = place(1L, "기장 식당", "39", "129.2150", "35.2440");
        Place nearbyAttraction = place(2L, "부산역 광장", "12", "129.0450", "35.1180");
        Place nearbyMeal = place(3L, "부산역 식당", "39", "129.0460", "35.1185");
        ScheduleRepairContext context = context(
                List.of(List.of(distantMeal)),
                List.of(distantMeal, nearbyAttraction, nearbyMeal), Set.of(), Set.of());

        RepairCandidate candidate = new NearbyReplacementRepair().repair(context).get(0);

        assertThat(candidate.placesByDay().get(0)).containsExactly(nearbyMeal);
    }

    @Test
    void movesOnlyAnOptionalPlaceToAnotherDayWithCapacity() {
        Place first = place(1L, "남포 산책", "129.0320", "35.1010");
        Place second = place(2L, "광안리 해변", "129.1180", "35.1530");
        Place third = place(3L, "기장 해안", "129.2150", "35.2440");
        ScheduleRepairContext context = context(
                List.of(List.of(first, second, third), List.of()),
                List.of(first, second, third), Set.of(), Set.of());

        RepairCandidate candidate = new CrossDayMoveRepair().repair(context).get(0);

        assertThat(candidate.type()).isEqualTo(RepairCandidate.RepairType.CROSS_DAY_MOVE);
        assertThat(candidate.placesByDay().get(0)).hasSize(2);
        assertThat(candidate.placesByDay().get(1)).singleElement();
    }

    @Test
    void removesOnlyOptionalPlaceAsLastResort() {
        Place required = place(1L, "필수 장소", "129.0320", "35.1010");
        Place optional = place(2L, "선택 장소", "129.2150", "35.2440");
        ScheduleRepairContext context = context(
                List.of(List.of(required, optional)), List.of(required, optional), Set.of(1L), Set.of());

        RepairCandidate candidate = new LowUtilityRemovalRepair().repair(context).get(0);

        assertThat(candidate.type()).isEqualTo(RepairCandidate.RepairType.LOW_UTILITY_REMOVAL);
        assertThat(candidate.placesByDay().get(0)).containsExactly(required);
    }

    private ScheduleRepairEngine engine() {
        return new ScheduleRepairEngine(
                new RouteReorderRepair(),
                new StayDurationRepair(),
                new NearbyReplacementRepair(),
                new CrossDayMoveRepair(),
                new LowUtilityRemovalRepair());
    }

    private ScheduleRepairContext context(
            List<List<Place>> placesByDay,
            List<Place> allCandidates,
            Set<Long> mustVisitPlaceIds,
            Set<Long> fixedEventPlaceIds
    ) {
        List<ScheduleDay> days = days(placesByDay.size());
        return new ScheduleRepairContext(
                0,
                placesByDay,
                allCandidates,
                placesByDay.get(0),
                days,
                java.util.stream.IntStream.range(0, days.size())
                        .mapToObj(ignored -> new PlaceCountPolicy(2, 3, 4, 5, 70))
                        .toList(),
                mustVisitPlaceIds,
                fixedEventPlaceIds,
                request(),
                scorer
        );
    }

    private List<ScheduleDay> days(int count) {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-21"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                "부산역", decimal("129.0403"), decimal("35.1151"),
                "부산역", decimal("129.0403"), decimal("35.1151"), "test", "{}"
        );
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new ScheduleDay(
                        schedule, index + 1, LocalDate.parse("2026-07-20").plusDays(index),
                        LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                        "부산역", decimal("129.0403"), decimal("35.1151"),
                        "부산역", decimal("129.0403"), decimal("35.1151")))
                .toList();
    }

    private ScheduleCreateRequest request() {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                new ScheduleCreateRequest.Location("부산역", decimal("129.0403"), decimal("35.1151")),
                new ScheduleCreateRequest.Location("부산역", decimal("129.0403"), decimal("35.1151")),
                List.of(), List.of());
    }

    private Place place(Long id, String name, String longitude, String latitude) {
        return place(id, name, "12", longitude, latitude);
    }

    private Place place(Long id, String name, String contentTypeId, String longitude, String latitude) {
        Place place = new Place(
                "LOCAL_FIXTURE", name, contentTypeId, name, "관광지", "부산",
                decimal(longitude), decimal(latitude), null);
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
