package com.server.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.web.TraceIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 오류 응답이 code·fieldErrors·traceId 형태를 지키는지 확인한다.
 * 예전에는 아래 경우들이 Spring 기본 응답(timestamp/status/error/path)으로 나가
 * 클라이언트가 code로 분기할 수 없었다.
 */
@DisplayName("공통 오류 응답")
class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {

        record Body(String name) { }

        @GetMapping("/api/v1/places/{placeId}")
        String typed(@PathVariable Long placeId) {
            return "ok";
        }

        @GetMapping("/api/v1/locations/search")
        String requiredParam(@RequestParam String keyword) {
            return "ok";
        }

        @PostMapping("/api/v1/schedule-previews")
        String body(@RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/api/v1/schedules/{scheduleId}")
        String boom(@PathVariable UUID scheduleId) {
            throw new IllegalStateException("예상치 못한 내부 오류");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("경로 변수 타입이 맞지 않으면 도메인 오류 코드와 필드 사유를 반환한다")
    void typeMismatchReturnsErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/places/abc").header("X-Trace-Id", "trace-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_SEARCH_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("placeId"))
                .andExpect(jsonPath("$.traceId").value("trace-1"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 없으면 어느 값이 빠졌는지 알려준다")
    void missingParameterReturnsErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/locations/search").header("X-Trace-Id", "trace-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_SEARCH_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("keyword"))
                .andExpect(jsonPath("$.traceId").value("trace-2"));
    }

    @Test
    @DisplayName("본문 JSON이 깨졌으면 형식 오류로 반환한다")
    void unreadableBodyReturnsErrorResponse() throws Exception {
        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-3")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("body"))
                .andExpect(jsonPath("$.traceId").value("trace-3"));
    }

    @Test
    @DisplayName("예상하지 못한 예외도 traceId를 담아 500으로 반환한다")
    void unexpectedExceptionReturnsErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/{id}", UUID.randomUUID())
                        .header("X-Trace-Id", "trace-4"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").value("trace-4"));
    }
}
