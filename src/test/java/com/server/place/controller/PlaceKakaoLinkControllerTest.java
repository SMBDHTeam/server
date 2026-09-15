package com.server.place.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.place.dto.PlaceKakaoLinkResponse;
import com.server.place.service.PlaceKakaoLinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("장소 카카오맵 주소 API")
class PlaceKakaoLinkControllerTest {

    private final PlaceKakaoLinkService placeKakaoLinkService = Mockito.mock(PlaceKakaoLinkService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PlaceKakaoLinkController(placeKakaoLinkService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @Test
    @DisplayName("찾은 카카오 장소 주소를 명세 응답으로 반환한다")
    void returnsMatchedLink() throws Exception {
        when(placeKakaoLinkService.getLink(101L)).thenReturn(PlaceKakaoLinkResponse.matched(101L, "7913306"));

        mockMvc.perform(get("/api/v1/places/101/kakao-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(101))
                .andExpect(jsonPath("$.kakaoPlaceId").value("7913306"))
                .andExpect(jsonPath("$.url").value("https://place.map.kakao.com/7913306"))
                .andExpect(jsonPath("$.matched").value(true));
    }

    @Test
    @DisplayName("없는 장소는 404 PLACE_NOT_FOUND 다")
    void missingPlaceIsNotFound() throws Exception {
        when(placeKakaoLinkService.getLink(999L)).thenThrow(new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/places/999/kakao-link"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }
}
