package com.server.schedule.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleScoreEvaluatorTest {

    private final ScheduleScoreEvaluator evaluator = new ScheduleScoreEvaluator();

    @Test
    void penalizesUnusedTimeOverNinetyMinutesUpToTenPoints() {
        LocalDate date = LocalDate.parse("2026-07-20");
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                date,
                date,
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                location("출발지"),
                location("도착지"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_RELAXED")),
                List.of()
        );
        ScheduleResponse.Stop stop = new ScheduleResponse.Stop(
                UUID.randomUUID(),
                1,
                60,
                new ScheduleResponse.Place(
                        1L, "장소", new BigDecimal("129.1"), new BigDecimal("35.1")),
                null
        );
        ScheduleResponse.Day day = new ScheduleResponse.Day(
                1,
                date,
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                new ScheduleResponse.DayLocation("출발지", new BigDecimal("129.1"), new BigDecimal("35.1")),
                new ScheduleResponse.DayLocation("장소", new BigDecimal("129.1"), new BigDecimal("35.1")),
                "USER",
                "LAST_STOP",
                "출발지 출발 → 장소",
                List.of(stop),
                null
        );
        ScheduleResponse response = new ScheduleResponse(
                UUID.randomUUID(), "CONFIRMED", date, date, "테스트", List.of(day));

        ScheduleScoreResult.Metric timeFit = evaluator.evaluate(request, response).metrics().stream()
                .filter(metric -> "TIME_FIT".equals(metric.id()))
                .findFirst()
                .orElseThrow();

        assertThat(timeFit.score()).isEqualTo(20);
        assertThat(timeFit.reason()).isEqualTo("가장 긴 미사용 시간 540분");
    }

    @Test
    void normalizesTransferBurdenByRouteCountForMultiDaySchedules() {
        LocalDate date = LocalDate.parse("2026-07-20");
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                date,
                date,
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                location("출발지"),
                location("도착지"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("TRANSIT", "TRANSIT_SIMPLE")),
                List.of()
        );
        List<ScheduleResponse.Stop> stops = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> new ScheduleResponse.Stop(
                        UUID.randomUUID(), index + 1, 30,
                        new ScheduleResponse.Place(
                                (long) index + 1, "장소 " + index,
                                new BigDecimal("129.1"), new BigDecimal("35.1")),
                        transitWithOneTransfer()))
                .toList();
        ScheduleResponse.Day day = new ScheduleResponse.Day(
                1, date, LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                null, null, "USER", "LAST_STOP", "테스트", stops, null);
        ScheduleResponse response = new ScheduleResponse(
                UUID.randomUUID(), "CONFIRMED", date, date, "테스트", List.of(day));

        ScheduleScoreResult.Metric transitFit = evaluator.evaluate(request, response).metrics().stream()
                .filter(metric -> "TRANSIT_FIT".equals(metric.id()))
                .findFirst()
                .orElseThrow();

        assertThat(transitFit.score()).isEqualTo(12);
        assertThat(transitFit.reason())
                .isEqualTo("복합 대중교통 구간 부담 10, 이동 구간 10개, 구간당 1.00");
    }

    private ScheduleResponse.Transit transitWithOneTransfer() {
        return new ScheduleResponse.Transit(
                30,
                1500,
                List.of(
                        new ScheduleResponse.Segment("BUS", "1001", "A", "B"),
                        new ScheduleResponse.Segment("SUBWAY", "1호선", "B", "C")
                ));
    }

    private ScheduleCreateRequest.Location location(String name) {
        return new ScheduleCreateRequest.Location(
                name, new BigDecimal("129.1"), new BigDecimal("35.1"));
    }
}
