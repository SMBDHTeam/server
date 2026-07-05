package com.server.schedule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.service.ScheduleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
                .andExpect(jsonPath("$.days[0].stops[0].place.id").value(101))
                .andExpect(jsonPath("$.days[0].stops[0].inboundTransit.totalMinutes").value(25))
                .andExpect(jsonPath("$.days[0].stops[0].inboundTransit.segments[0].mode").value("BUS"))
                .andExpect(jsonPath("$.days[0].finalTransit.totalMinutes").value(25));
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
                .andExpect(jsonPath("$.routeLines[0].mode").value("SUBWAY"))
                .andExpect(jsonPath("$.routeLines[0].startName").value("부산역"))
                .andExpect(jsonPath("$.routeLines[0].endName").value("중앙역"))
                .andExpect(jsonPath("$.routeLines[0].coordinates[0][0]").value(129.039323));
    }

    private ScheduleResponse response() {
        return new ScheduleResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "CONFIRMED",
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-24"),
                "COMPANION:COMPANION_PARENTS",
                List.of(new ScheduleResponse.Day(
                        1,
                        LocalDate.parse("2026-06-23"),
                        List.of(new ScheduleResponse.Stop(
                                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                                1,
                                60,
                                new ScheduleResponse.Place(
                                        101L,
                                        "이송도전망대",
                                        new BigDecimal("129.047956"),
                                        new BigDecimal("35.075519")
                                ),
                                transit()
                        )),
                        transit()
                ))
        );
    }

    private ScheduleResponse.Transit transit() {
        return new ScheduleResponse.Transit(
                25,
                1550,
                List.of(new ScheduleResponse.Segment("BUS", "26", "부산역", "남부민2동"))
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
                        List.of(List.of(new BigDecimal("129.039323"), new BigDecimal("35.114494")))
                ))
        );
    }
}
