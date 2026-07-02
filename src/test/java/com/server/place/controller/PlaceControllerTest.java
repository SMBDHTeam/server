package com.server.place.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.place.dto.PlaceDetailResponse;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.service.PlaceService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("장소 API")
class PlaceControllerTest {

    private final PlaceService placeService = Mockito.mock(PlaceService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PlaceController(placeService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("장소 검색 결과를 명세 응답으로 반환한다")
    void searchReturnsPlaces() throws Exception {
        when(placeService.search("전망대", null, null, null))
                .thenReturn(new PlaceSearchResponse(List.of(new PlaceSearchResponse.Item(
                        101L,
                        "126508",
                        "이송도전망대",
                        "관광지",
                        "부산 서구 암남동",
                        new BigDecimal("129.047956"),
                        new BigDecimal("35.075519"),
                        null,
                        "https://example.com/image.jpg"
                ))));

        mockMvc.perform(get("/api/v1/places")
                        .param("keyword", "전망대")
                        .header("X-Trace-Id", "trace-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-test"))
                .andExpect(jsonPath("$.items[0].id").value(101))
                .andExpect(jsonPath("$.items[0].externalContentId").value("126508"))
                .andExpect(jsonPath("$.items[0].name").value("이송도전망대"))
                .andExpect(jsonPath("$.items[0].category").value("관광지"))
                .andExpect(jsonPath("$.items[0].address").value("부산 서구 암남동"))
                .andExpect(jsonPath("$.items[0].longitude").value(129.047956))
                .andExpect(jsonPath("$.items[0].latitude").value(35.075519))
                .andExpect(jsonPath("$.items[0].primaryImageUrl").value("https://example.com/image.jpg"));

        verify(placeService).search("전망대", null, null, null);
    }

    @Test
    @DisplayName("장소 상세 결과를 명세 응답으로 반환한다")
    void getDetailReturnsPlaceDetail() throws Exception {
        when(placeService.getDetail(101L))
                .thenReturn(new PlaceDetailResponse(
                        101L,
                        "126508",
                        "12",
                        "이송도전망대",
                        "부산 서구 암남동",
                        new BigDecimal("129.047956"),
                        new BigDecimal("35.075519"),
                        "장소 설명",
                        new PlaceDetailResponse.OperatingInfo("09:00~18:00", "연중무휴", "무료", "주차 가능", true),
                        List.of(new PlaceDetailResponse.Image(
                                "https://example.com/image.jpg",
                                "https://example.com/thumbnail.jpg",
                                "Type1"
                        ))
                ));

        mockMvc.perform(get("/api/v1/places/101")
                        .header("X-Trace-Id", "trace-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "trace-test"))
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.externalContentId").value("126508"))
                .andExpect(jsonPath("$.contentTypeId").value("12"))
                .andExpect(jsonPath("$.overview").value("장소 설명"))
                .andExpect(jsonPath("$.operatingInfo.openingHoursText").value("09:00~18:00"))
                .andExpect(jsonPath("$.operatingInfo.requiresManualCheck").value(true))
                .andExpect(jsonPath("$.images[0].url").value("https://example.com/image.jpg"));
    }

    @Test
    @DisplayName("장소가 없으면 404 오류 응답을 반환한다")
    void missingPlaceReturns404ErrorResponse() throws Exception {
        when(placeService.getDetail(404L))
                .thenThrow(new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/places/404")
                        .header("X-Trace-Id", "trace-test"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("장소를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").value("trace-test"));
    }
}
