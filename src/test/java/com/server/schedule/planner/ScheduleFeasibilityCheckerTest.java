package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitRoute;
import com.server.schedule.domain.TransitSegment;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleFeasibilityCheckerTest {

    private final ScheduleFeasibilityChecker checker = new ScheduleFeasibilityChecker();

    @Test
    void rejectsLowWalkItineraryWithAnExcessiveWalkingLeg() {
        ScheduleDay day = day();
        TransitRoute route = route(day, 41);
        new TransitSegment(route, 1, "WALK", null, null, "출발", null, "정류장", null,
                31, 2_100, null, 0, "UNAVAILABLE");
        new TransitSegment(route, 2, "BUS", "67", null, "정류장", null, "도착", null,
                10, 2_500, null, 0, "UNAVAILABLE");

        assertThat(checker.fitsTravelPreferences(day, request("MOBILITY_LOW_WALK"))).isFalse();
    }

    @Test
    void rejectsDailyTransitThatWouldOverwhelmTheVisitSchedule() {
        ScheduleDay day = day();
        TransitRoute first = route(day, 55);
        TransitRoute second = route(day, 55);
        TransitRoute third = route(day, 45);
        new TransitSegment(first, 1, "BUS", "1", null, "출발", null, "중간", null,
                55, 10_000, null, 0, "UNAVAILABLE");
        new TransitSegment(second, 1, "BUS", "2", null, "중간", null, "도착", null,
                55, 10_000, null, 0, "UNAVAILABLE");
        new TransitSegment(third, 1, "BUS", "3", null, "도착", null, "종료", null,
                45, 10_000, null, 0, "UNAVAILABLE");

        assertThat(checker.fitsTravelPreferences(day, request())).isFalse();
    }

    @Test
    void treatsAPromptHintAsStricterThanNoPreferenceButLooserThanAnExplicitLimit() {
        ScheduleDay day = day();
        TransitRoute route = route(day, 30);
        new TransitSegment(route, 1, "WALK", null, null, "출발", null, "정류장", null,
                22, 1_500, null, 0, "UNAVAILABLE");
        new TransitSegment(route, 2, "BUS", "67", null, "정류장", null, "도착", null,
                8, 2_500, null, 0, "UNAVAILABLE");

        assertThat(checker.fitsTravelPreferences(day, request())).isTrue();
        assertThat(checker.fitsTravelPreferences(day, request("PROMPT_LOW_WALKING"))).isFalse();
        assertThat(checker.fitsTravelPreferences(day, request("MOBILITY_LOW_WALK"))).isFalse();
    }

    @Test
    void rejectsAPromptHintItineraryWhoseWalkingAddsUpAcrossShortLegs() {
        ScheduleDay day = day();
        for (int index = 0; index < 4; index++) {
            TransitRoute route = route(day, 18);
            new TransitSegment(route, 1, "WALK", null, null, "출발", null, "도착", null,
                    18, 1_200, null, 0, "UNAVAILABLE");
        }

        assertThat(checker.fitsTravelPreferences(day, request())).isTrue();
        assertThat(checker.fitsTravelPreferences(day, request("PROMPT_LOW_WALKING"))).isFalse();
    }

    private ScheduleDay day() {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-10"),
                LocalTime.of(10, 0), LocalTime.of(20, 0),
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"),
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"),
                "test", "{}");
        ScheduleDay day = new ScheduleDay(schedule, 1, LocalDate.parse("2026-08-10"));
        Place place = new Place(
                "TEST", "place", "12", "장소", "관광지", "부산",
                new BigDecimal("129.0500"), new BigDecimal("35.1200"), null);
        new ScheduleStop(day, place, 1, 60);
        return day;
    }

    private TransitRoute route(ScheduleDay day, int minutes) {
        return new TransitRoute(day, null, "FINAL", day.getTransitRoutes().size() + 1,
                minutes, null, "ODSAY", "UNAVAILABLE", false, "[]", "{}");
    }

    private ScheduleCreateRequest request(String... answerIds) {
        ScheduleCreateRequest.Location location = new ScheduleCreateRequest.Location(
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"));
        List<ScheduleCreateRequest.SelectedAnswer> answers = java.util.Arrays.stream(answerIds)
                .map(answerId -> new ScheduleCreateRequest.SelectedAnswer("MOBILITY", answerId))
                .toList();
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-10"),
                LocalTime.of(10, 0), LocalTime.of(20, 0), location, location, answers, List.of(), List.of());
    }
}
