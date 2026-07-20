package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class MealTimePolicyTest {

    @Test
    void exposesLunchAndDinnerForAFullDay() {
        ScheduleDay day = day("09:00", "19:00");

        assertThat(MealTimePolicy.activeSlots(day))
                .containsExactly(MealTimePolicy.MealSlot.LUNCH, MealTimePolicy.MealSlot.DINNER);
        assertThat(MealTimePolicy.requiredMealStops(day, 3)).isEqualTo(1);
        assertThat(MealTimePolicy.requiredMealStops(day, 5)).isEqualTo(2);
    }

    @Test
    void alignsFirstAndSecondMealPlacesToLunchAndDinner() {
        ScheduleDay day = day("09:00", "19:00");
        Place restaurant = place("부산 로컬 식당");
        var assigned = EnumSet.noneOf(MealTimePolicy.MealSlot.class);

        MealTimePolicy.Alignment lunch = MealTimePolicy.alignArrival(
                LocalTime.parse("10:20"), restaurant, MealTimePolicy.activeSlots(day), assigned);
        assigned.add(lunch.slot());
        MealTimePolicy.Alignment dinner = MealTimePolicy.alignArrival(
                LocalTime.parse("13:10"), restaurant, MealTimePolicy.activeSlots(day), assigned);

        assertThat(lunch.arrival()).isEqualTo(LocalTime.parse("11:00"));
        assertThat(lunch.slot()).isEqualTo(MealTimePolicy.MealSlot.LUNCH);
        assertThat(dinner.arrival()).isEqualTo(LocalTime.parse("13:10"));
        assertThat(dinner.slot()).isNull();
    }

    @Test
    void alignsDinnerOnlyWhenEarlyWaitIsReasonable() {
        ScheduleDay day = day("09:00", "19:00");
        Place restaurant = place("광안리 저녁 식당");
        var assigned = EnumSet.of(MealTimePolicy.MealSlot.LUNCH);

        MealTimePolicy.Alignment dinner = MealTimePolicy.alignArrival(
                LocalTime.parse("15:50"), restaurant, MealTimePolicy.activeSlots(day), assigned);

        assertThat(dinner.arrival()).isEqualTo(LocalTime.parse("17:00"));
        assertThat(dinner.slot()).isEqualTo(MealTimePolicy.MealSlot.DINNER);
    }

    private ScheduleDay day(String startTime, String endTime) {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-20"),
                LocalTime.parse(startTime), LocalTime.parse(endTime),
                "부산역", decimal("129.0403"), decimal("35.1151"),
                "부산역", decimal("129.0403"), decimal("35.1151"), "test", "{}");
        return new ScheduleDay(schedule, 1, LocalDate.parse("2026-07-20"));
    }

    private Place place(String name) {
        return new Place(
                "TOUR_API", name, "39", name, "음식점", "부산",
                decimal("129.0500"), decimal("35.1200"), null);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
