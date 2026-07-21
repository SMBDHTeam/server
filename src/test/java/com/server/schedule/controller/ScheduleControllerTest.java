package com.server.schedule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleEvaluationReport;
import com.server.schedule.dto.ScheduleListResponse;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.service.ScheduleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("일정 API")
class ScheduleControllerTest {

    private final ScheduleService scheduleService = Mockito.mock(ScheduleService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ScheduleController(scheduleService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("일정과 대중교통 경로를 생성하고 201 응답을 반환한다")
    void createReturnsScheduleWithTransitRoutes() throws Exception {
        when(scheduleService.create(any(ScheduleCreateRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-test")
                        .content("""
                                {
                                  "startDate": "2026-06-23",
                                  "endDate": "2026-06-24",
                                  "dailyStartTime": "09:00",
                                  "dailyEndTime": "19:00",
                                  "startLocation": {
                                    "name": "부산역",
                                    "longitude": 129.0403,
                                    "latitude": 35.1151
                                  },
                                  "endLocation": {
                                    "name": "부산역",
                                    "longitude": 129.0403,
                                    "latitude": 35.1151
                                  },
                                  "days": [
                                    {
                                      "dayNo": 1,
                                      "startTime": "10:00",
                                      "endTime": "20:00",
                                      "startLocation": {"name": "부산역", "longitude": 129.0403, "latitude": 35.1151},
                                      "endLocation": {"name": "숙소 A", "longitude": 129.158, "latitude": 35.159}
                                    },
                                    {
                                      "dayNo": 2,
                                      "startTime": "09:00",
                                      "endTime": "17:00",
                                      "startLocation": {"name": "숙소 A", "longitude": 129.158, "latitude": 35.159},
                                      "endLocation": {"name": "김해국제공항", "longitude": 128.9485, "latitude": 35.1732}
                                    }
                                  ],
                                  "selectedAnswers": [
                                    {
                                      "questionId": "COMPANION",
                                      "answerId": "COMPANION_PARENTS"
                                    }
                                  ],
                                  "mustVisitPlaceIds": [101]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "trace-test"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.dailyStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.days[0].stops[0].place.id").value(101))
                .andExpect(jsonPath("$.days[0].stops[0].arriveAt").value("09:25:00"))
                .andExpect(jsonPath("$.days[0].stops[0].selectionReasons[0]").value("사용자가 반드시 방문할 장소로 선택했습니다."))
                .andExpect(jsonPath("$.days[0].stops[0].inboundTransit.totalMinutes").value(25))
                .andExpect(jsonPath("$.days[0].stops[0].inboundTransit.transferCount").value(0))
                .andExpect(jsonPath("$.days[0].stops[0].inboundTransit.segments[0].mode").value("BUS"))
                .andExpect(jsonPath("$.days[0].stops[0].inboundTransit.segments[0].instruction").value("부산역에서 26 승차 후 남부민2동에서 하차"))
                .andExpect(jsonPath("$.days[0].finalTransit.totalMinutes").value(25))
                .andExpect(jsonPath("$.evaluation.hardGate.passed").value(true))
                .andExpect(jsonPath("$.evaluation.qualityScore.totalScore").value(95))
                .andExpect(jsonPath("$.evaluation.operations.providerCallCount").value(4))
                .andExpect(jsonPath("$.evaluation.operations.externalHttpCallCount").value(12))
                .andExpect(jsonPath("$.evaluation.operations.geometryFallbackLineCount").value(0));

        ArgumentCaptor<ScheduleCreateRequest> captor = ArgumentCaptor.forClass(ScheduleCreateRequest.class);
        Mockito.verify(scheduleService).create(captor.capture());
        ScheduleCreateRequest captured = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(captured.daysOrEmpty()).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(captured.daysOrEmpty().get(0).endLocation().name()).isEqualTo("숙소 A");
        org.assertj.core.api.Assertions.assertThat(captured.daysOrEmpty().get(1).startLocation().name()).isEqualTo("숙소 A");
    }

    @Test
    @DisplayName("필수 요청 필드가 없으면 400 오류 응답을 반환한다")
    void invalidRequestReturns400ErrorResponse() throws Exception {
        mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-test")
                        .content("""
                                {
                                  "endDate": "2026-06-24",
                                  "dailyStartTime": "09:00",
                                  "dailyEndTime": "19:00",
                                  "selectedAnswers": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_CONDITION"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").value("trace-test"));
    }

    @Test
    @DisplayName("일정 지도 마커와 경로선을 조회한다")
    void getMapReturnsMarkersAndRouteLines() throws Exception {
        UUID scheduleId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(scheduleService.getMap(scheduleId, 1)).thenReturn(mapResponse());

        mockMvc.perform(get("/api/v1/schedules/{scheduleId}/map", scheduleId)
                        .param("dayNo", "1")
                        .header("X-Trace-Id", "trace-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-test"))
                .andExpect(jsonPath("$.startMarker.name").value("부산역"))
                .andExpect(jsonPath("$.markers[0].placeId").value(101))
                .andExpect(jsonPath("$.markers[0].arriveAt").value("09:25:00"))
                .andExpect(jsonPath("$.markers[0].subtitle").value("관광지 · 체류 60분"))
                .andExpect(jsonPath("$.routeLines[0].mode").value("SUBWAY"))
                .andExpect(jsonPath("$.routeLines[0].startName").value("부산역"))
                .andExpect(jsonPath("$.routeLines[0].endName").value("중앙역"))
                .andExpect(jsonPath("$.routeLines[0].instruction").value("부산역에서 부산 1호선 승차 후 중앙역에서 하차"))
                .andExpect(jsonPath("$.routeLines[0].coordinates[0][0]").value(129.039323));
    }

    @Test
    @DisplayName("전체 일정 목록을 조회한다")
    void getAllReturnsSchedules() throws Exception {
        when(scheduleService.getAll()).thenReturn(new ScheduleListResponse(List.of(responseWithoutEvaluation())));

        mockMvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].days[0].stops[0].place.id").value(101))
                .andExpect(jsonPath("$.items[0].evaluation").doesNotExist());
    }

    @Test
    @DisplayName("전체 방문 계획을 반영해 일정을 수정한다")
    void updateReturnsRecalculatedSchedule() throws Exception {
        UUID scheduleId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(scheduleService.update(Mockito.eq(scheduleId), any())).thenReturn(response());

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stops": [
                                    {
                                      "stopId": "00000000-0000-0000-0000-000000000101",
                                      "dayNo": 1,
                                      "order": 1,
                                      "stayMinutes": 70
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.days[0].finalTransit.totalMinutes").value(25));
    }

    @Test
    @DisplayName("수정 항목에 stopId와 placeId를 함께 전달하면 400을 반환한다")
    void updateRejectsAmbiguousStopReference() throws Exception {
        UUID scheduleId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stops": [
                                    {
                                      "stopId": "00000000-0000-0000-0000-000000000101",
                                      "placeId": 205,
                                      "dayNo": 1,
                                      "order": 1,
                                      "stayMinutes": 70
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_CONDITION"));
    }

    private ScheduleResponse response() {
        return new ScheduleResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CONFIRMED",
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-24"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                "COMPANION:COMPANION_PARENTS",
                List.of(new ScheduleResponse.Day(
                        1,
                        LocalDate.parse("2026-06-23"),
                        LocalTime.parse("09:00"),
                        LocalTime.parse("19:00"),
                        "부산역 출발 → 이송도전망대 → 부산역 도착",
                        List.of(new ScheduleResponse.Stop(
                                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                                1,
                                LocalTime.parse("09:25"),
                                LocalTime.parse("10:25"),
                                60,
                                new ScheduleResponse.Place(
                                        101L,
                                        "이송도전망대",
                                        "관광지",
                                        "부산",
                                        new BigDecimal("129.047956"),
                                        new BigDecimal("35.075519"),
                                        null,
                                        null
                                ),
                                transit(),
                                List.of("사용자가 반드시 방문할 장소로 선택했습니다."),
                                List.of()
                        )),
                        transit()
                )),
                new ScheduleEvaluationReport(
                        new ScheduleEvaluationReport.HardGate(true, List.of()),
                        new ScheduleEvaluationReport.QualityScore(
                                95,
                                100,
                                List.of(new ScheduleEvaluationReport.Metric(
                                        "TIME_FIT", "일정 시간 적합성", 30, 30, "일별 가용 시간 안에 들어옴"
                                ))
                        ),
                        new ScheduleEvaluationReport.Operations(
                                1200, "AI_GENERATED", 91, 3, 2,
                                8, 2, 6, 0,
                                6, 2, 4, 0,
                                12, 0, 4, 4, 4,
                                4, 0, 0, 100, 10, 0, List.of("ODSAY")
                        )
                )
        );
    }

    private ScheduleResponse responseWithoutEvaluation() {
        ScheduleResponse response = response();
        return new ScheduleResponse(
                response.id(),
                response.status(),
                response.startDate(),
                response.endDate(),
                response.dailyStartTime(),
                response.dailyEndTime(),
                response.styleSummary(),
                response.days(),
                null
        );
    }

    private ScheduleResponse.Transit transit() {
        return new ScheduleResponse.Transit(
                "INBOUND",
                1,
                "부산역",
                "이송도전망대",
                "26",
                LocalTime.parse("09:00"),
                LocalTime.parse("09:25"),
                25,
                0,
                0,
                0,
                1550,
                "ODSAY",
                "UNAVAILABLE",
                false,
                List.of(new ScheduleResponse.Segment(
                        1,
                        "BUS",
                        "26",
                        null,
                        "부산역",
                        null,
                        "남부민2동",
                        "부산역에서 26 승차 후 남부민2동에서 하차",
                        25,
                        null,
                        null,
                        0,
                        "UNAVAILABLE"
                )),
                List.of()
        );
    }

    private ScheduleMapResponse mapResponse() {
        return new ScheduleMapResponse(
                new ScheduleMapResponse.Marker("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleMapResponse.Marker("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                List.of(new ScheduleMapResponse.StopMarker(
                        1,
                        1,
                        101L,
                        "부산타워",
                        LocalTime.parse("09:25"),
                        LocalTime.parse("10:25"),
                        "관광지 · 체류 60분",
                        "NORMAL",
                        new BigDecimal("129.032338"),
                        new BigDecimal("35.101243")
                )),
                List.of(new ScheduleMapResponse.RouteLine(
                        1,
                        1,
                        1,
                        "SUBWAY",
                        "부산 1호선",
                        "부산역",
                        "중앙역",
                        11,
                        null,
                        "부산역에서 부산 1호선 승차 후 중앙역에서 하차",
                        false,
                        List.of(List.of(new BigDecimal("129.039323"), new BigDecimal("35.114494")))
                ))
        );
    }
}
