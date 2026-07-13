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

class DayPlaceAllocatorTest {

    private final DayPlaceAllocator allocator = new DayPlaceAllocator();

    @Test
    void assignsPlacesToTheDayWithTheClosestEndpoints() {
        Schedule schedule = schedule();
        ScheduleDay westDay = day(schedule, 1, "서부산 숙소", "129.0300", "35.1000");
        ScheduleDay eastDay = day(schedule, 2, "해운대 숙소", "129.1600", "35.1600");
        Place westPlace = place("남포 시장", "129.0320", "35.1010");
        Place eastPlace = place("해운대 해변", "129.1580", "35.1590");

        List<List<Place>> result = allocator.allocate(
                List.of(eastPlace, westPlace),
                List.of(westDay, eastDay),
                List.of(1, 1)
        );

        assertThat(result.get(0)).extracting(Place::getName).containsExactly("남포 시장");
        assertThat(result.get(1)).extracting(Place::getName).containsExactly("해운대 해변");
    }

    private Schedule schedule() {
        return new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-21"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"),
                "김해국제공항", new BigDecimal("128.9485"), new BigDecimal("35.1732"),
                "test", "{}"
        );
    }

    private ScheduleDay day(Schedule schedule, int dayNo, String name, String longitude, String latitude) {
        return new ScheduleDay(
                schedule, dayNo, LocalDate.parse("2026-07-19").plusDays(dayNo),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                name, new BigDecimal(longitude), new BigDecimal(latitude),
                name, new BigDecimal(longitude), new BigDecimal(latitude)
        );
    }

    private Place place(String name, String longitude, String latitude) {
        return new Place(
                "TOUR_API", name, "12", name, "관광지", "부산",
                new BigDecimal(longitude), new BigDecimal(latitude), null
        );
    }
}
