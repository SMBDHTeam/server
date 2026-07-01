package com.server.facility.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.facility.service.NearbyFacilityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("주변 편의시설 API")
class NearbyFacilityControllerTest {

    private final NearbyFacilityService nearbyFacilityService = Mockito.mock(NearbyFacilityService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new NearbyFacilityController(nearbyFacilityService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("지원하지 않는 편의시설 유형은 501 오류 응답을 반환한다")
    void unsupportedFacilityTypeReturns501ErrorResponse() throws Exception {
        when(nearbyFacilityService.search(1L, "ATM", 1000))
                .thenThrow(new BusinessException(ErrorCode.FACILITY_TYPE_NOT_SUPPORTED));

        mockMvc.perform(get("/api/v1/places/1/nearby-facilities")
                        .param("types", "ATM")
                        .param("radius", "1000")
                        .header("X-Trace-Id", "trace-test"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("FACILITY_TYPE_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 편의시설 유형입니다."))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").value("trace-test"));
    }
}
