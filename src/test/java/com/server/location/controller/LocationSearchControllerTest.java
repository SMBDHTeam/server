package com.server.location.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.location.dto.LocationSearchResponse;
import com.server.location.service.LocationSearchService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("출발지·도착지 검색 API")
class LocationSearchControllerTest {

    private final LocationSearchService locationSearchService = Mockito.mock(LocationSearchService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new LocationSearchController(locationSearchService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("Kakao Local 검색 결과를 명세 응답으로 반환한다")
    void searchReturnsKakaoLocalItems() throws Exception {
        when(locationSearchService.search("부산역", 10))
                .thenReturn(new LocationSearchResponse(List.of(new LocationSearchResponse.Item(
                        "부산역",
                        "부산 동구 중앙대로 206",
                        new BigDecimal("129.0403"),
                        new BigDecimal("35.1151"),
                        "kakao-place-id",
                        "KAKAO_LOCAL"
                ))));

        mockMvc.perform(get("/api/v1/locations/search")
                        .param("keyword", "부산역")
                        .header("X-Trace-Id", "trace-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-test"))
                .andExpect(jsonPath("$.items[0].name").value("부산역"))
                .andExpect(jsonPath("$.items[0].address").value("부산 동구 중앙대로 206"))
                .andExpect(jsonPath("$.items[0].longitude").value(129.0403))
                .andExpect(jsonPath("$.items[0].latitude").value(35.1151))
                .andExpect(jsonPath("$.items[0].externalId").value("kakao-place-id"))
                .andExpect(jsonPath("$.items[0].source").value("KAKAO_LOCAL"));

        verify(locationSearchService).search("부산역", 10);
    }
}
