package com.server.share.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.GlobalExceptionHandler;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.share.dto.ShareLinkCreateRequest;
import com.server.share.dto.ShareLinkResponse;
import com.server.share.dto.SharedScheduleResponse;
import com.server.share.service.ShareService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("일정 공유 API")
class ShareControllerTest {

    private final ShareService shareService = Mockito.mock(ShareService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ShareController(shareService), new SharedScheduleController(shareService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("공유 링크를 생성하고 201을 반환한다")
    void createShareLink() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(shareService.create(Mockito.eq(scheduleId), any(ShareLinkCreateRequest.class)))
                .thenReturn(new ShareLinkResponse(
                        UUID.randomUUID(), "raw-token", "/shared-schedules/raw-token",
                        OffsetDateTime.parse("2026-08-08T12:00:00+09:00")
                ));

        mockMvc.perform(post("/api/v1/schedules/{scheduleId}/shares", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInDays\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("raw-token"))
                .andExpect(jsonPath("$.url").value("/shared-schedules/raw-token"));
    }

    @Test
    @DisplayName("공유 일정과 지도를 읽기 전용으로 조회한다")
    void getSharedScheduleAndMap() throws Exception {
        ScheduleResponse schedule = scheduleResponse();
        when(shareService.getSharedSchedule("raw-token"))
                .thenReturn(SharedScheduleResponse.from(schedule));
        when(shareService.getSharedMap("raw-token", 1)).thenReturn(new ScheduleMapResponse(
                new ScheduleMapResponse.Marker("부산역", new BigDecimal("129.04"), new BigDecimal("35.11")),
                new ScheduleMapResponse.Marker("부산역", new BigDecimal("129.04"), new BigDecimal("35.11")),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/v1/shared-schedules/raw-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/v1/shared-schedules/raw-token/map").param("dayNo", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startMarker.name").value("부산역"));
    }

    @Test
    @DisplayName("공유 링크를 폐기하고 204를 반환한다")
    void revokeShareLink() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        doNothing().when(shareService).revoke(scheduleId, shareId);

        mockMvc.perform(delete("/api/v1/schedules/{scheduleId}/shares/{shareId}", scheduleId, shareId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("공유 유효기간이 365일을 넘으면 400을 반환한다")
    void createRejectsExcessiveExpiration() throws Exception {
        mockMvc.perform(post("/api/v1/schedules/{scheduleId}/shares", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInDays\":366}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_CONDITION"));
    }

    private ScheduleResponse scheduleResponse() {
        return new ScheduleResponse(
                UUID.randomUUID(),
                "CONFIRMED",
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-01"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                "테스트",
                List.of(),
                null
        );
    }
}
