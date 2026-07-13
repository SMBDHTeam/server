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

    private ScheduleCreateRequest request() {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-21"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                location("부산역", "129.0403", "35.1151"),
                location("김해국제공항", "128.9485", "35.1732"),
                List.of(
                        new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS"),
                        new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_LOW_WALK")
                ),
                List.of()
        );
    }

    private ScheduleCreateRequest.Location location(String name, String longitude, String latitude) {
        return new ScheduleCreateRequest.Location(name, decimal(longitude), decimal(latitude));
    }

    private Place place(Long id, String name, String longitude, String latitude) {
        Place place = new Place(
                "LOCAL_FIXTURE", name, "12", name, "관광지", "부산",
                decimal(longitude), decimal(latitude), null
        );
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
