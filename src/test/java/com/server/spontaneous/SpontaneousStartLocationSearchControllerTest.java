package com.server.spontaneous;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalRegionCodeResponse;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.external.spontaneous.FastApiSpontaneousClient;
import com.server.location.controller.LocationSearchController;
import com.server.location.service.LocationSearchService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("즉흥여행 부산 출발지 검색과 기존 API 회귀 검증")
class SpontaneousStartLocationSearchControllerTest {

    private static final String SEARCH_PATH = "/api/v1/spontaneous-trips/start-locations/search";

    private final KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
    private final FastApiSpontaneousClient fastApiSpontaneousClient = mock(FastApiSpontaneousClient.class);
    private final LocationSearchService locationSearchService = new LocationSearchService(kakaoLocalClient);
    private final SpontaneousStartLocationValidator validator =
            new SpontaneousStartLocationValidator(kakaoLocalClient);
    // 외부 Client만 mock으로 두어 Controller → Service → Validator의 실제 흐름을 검증한다.
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new SpontaneousStartLocationSearchController(
                            new SpontaneousStartLocationSearchService(locationSearchService, validator)),
                    new LocationSearchController(locationSearchService),
                    new SpontaneousTripController(fastApiSpontaneousClient, validator))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @ParameterizedTest
    @ValueSource(strings = {"부산 동구 초량동 1187-1", "부산광역시 동구 초량동 1187-1"})
    @DisplayName("CASE 1, 4: 부산 후보는 행정구역 확인 후 기존 DTO로 반환한다")
    void returnsConfirmedBusanStation(String address) throws Exception {
        when(kakaoLocalClient.searchKeyword("부산역", 10))
                .thenReturn(new KakaoLocalSearchResponse(List.of(place("8329752", "부산역", address))));
        when(kakaoLocalClient.searchRegionCode(any(), any())).thenReturn(region("부산광역시"));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "부산역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("부산역"))
                .andExpect(jsonPath("$.items[0].address").value(address))
                .andExpect(jsonPath("$.items[0].longitude").value(129.0403))
                .andExpect(jsonPath("$.items[0].latitude").value(35.1151))
                .andExpect(jsonPath("$.items[0].externalId").value("8329752"))
                .andExpect(jsonPath("$.items[0].source").value("KAKAO_LOCAL"));

        verify(kakaoLocalClient).searchKeyword("부산역", 10);
        verify(kakaoLocalClient).searchRegionCode(new BigDecimal("129.0403"), new BigDecimal("35.1151"));
    }

    @Test
    @DisplayName("CASE 2: 서울역 검색은 행정구역 호출 없이 200 빈 배열을 반환한다")
    void excludesSeoulWithoutRegionLookup() throws Exception {
        when(kakaoLocalClient.searchKeyword("서울역", 10)).thenReturn(new KakaoLocalSearchResponse(
                List.of(place("seoul", "서울역", "서울 중구 한강대로 405"))));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "서울역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(kakaoLocalClient).searchKeyword("서울역", 10);
        verifyNoMoreInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("CASE 3: 혼합 결과는 부산만 확인하고 원래 순서대로 반환한다")
    void filtersMixedResultsPreservingOrder() throws Exception {
        when(kakaoLocalClient.searchKeyword("역", 3)).thenReturn(new KakaoLocalSearchResponse(List.of(
                place("busan", "부산역", "부산 동구 초량동"),
                place("seoul", "서울역", "서울 중구 봉래동"),
                new KakaoLocalSearchResponse.Document("bjeon", "부전역", "부산광역시 부산진구 부전동",
                        "129.0600", "35.1600", null, null))));
        when(kakaoLocalClient.searchRegionCode(any(), any())).thenReturn(region("부산광역시"));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "역").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].externalId").value("busan"))
                .andExpect(jsonPath("$.items[1].externalId").value("bjeon"));

        verify(kakaoLocalClient).searchKeyword("역", 3);
        verify(kakaoLocalClient).searchRegionCode(new BigDecimal("129.0403"), new BigDecimal("35.1151"));
        verify(kakaoLocalClient).searchRegionCode(new BigDecimal("129.06"), new BigDecimal("35.16"));
        verifyNoMoreInteractions(kakaoLocalClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"경상남도", "서울특별시", "경기도", "부산"})
    @DisplayName("CASE 5: 주소가 부산이어도 최종 행정구역이 부산광역시가 아니면 제외한다")
    void regionCodeOverridesAddress(String regionName) throws Exception {
        when(kakaoLocalClient.searchKeyword("경계 장소", 10)).thenReturn(new KakaoLocalSearchResponse(
                List.of(place("border", "경계 장소", "부산 강서구"))));
        when(kakaoLocalClient.searchRegionCode(any(), any())).thenReturn(region(regionName));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "경계 장소"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("CASE 6: 키워드 검색 장애는 즉흥여행 503으로 반환한다")
    void keywordFailureReturnsSpontaneous503() throws Exception {
        when(kakaoLocalClient.searchKeyword("부산역", 10))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "부산역"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SPONTANEOUS_PROVIDER_UNAVAILABLE"));

        verify(kakaoLocalClient).searchKeyword("부산역", 10);
        verifyNoMoreInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("CASE 7: 후보 확인 중 행정구역 장애가 나면 부분 결과 대신 503을 반환한다")
    void regionFailureDoesNotReturnPartialResults() throws Exception {
        when(kakaoLocalClient.searchKeyword("역", 10)).thenReturn(new KakaoLocalSearchResponse(List.of(
                place("first", "부산역", "부산 동구"), place("second", "부전역", "부산 부산진구"))));
        when(kakaoLocalClient.searchRegionCode(any(), any()))
                .thenReturn(region("부산광역시"))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "역"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SPONTANEOUS_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("CASE 8: 빈 검색어는 외부 호출 없이 400을 반환한다")
    void rejectsBlankKeyword(String keyword) throws Exception {
        mockMvc.perform(get(SEARCH_PATH).param("keyword", keyword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPONTANEOUS_TRIP_REQUEST"));

        verifyNoInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("필수 검색어 누락은 400을 반환한다")
    void rejectsMissingKeyword() throws Exception {
        mockMvc.perform(get(SEARCH_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPONTANEOUS_TRIP_REQUEST"));

        verifyNoInteractions(kakaoLocalClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    @DisplayName("CASE 9: size가 1 미만이거나 정수가 아니면 400을 반환한다")
    void rejectsInvalidSize(String size) throws Exception {
        mockMvc.perform(get(SEARCH_PATH).param("keyword", "부산역").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPONTANEOUS_TRIP_REQUEST"));

        verifyNoInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("CASE 10: 기존 공용 검색은 서울도 그대로 반환하고 부산 검증을 하지 않는다")
    void generalLocationSearchStillReturnsSeoul() throws Exception {
        when(kakaoLocalClient.searchKeyword("서울역", 10)).thenReturn(new KakaoLocalSearchResponse(
                List.of(place("seoul", "서울역", "서울 중구 한강대로 405"))));

        mockMvc.perform(get("/api/v1/locations/search").param("keyword", "서울역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("서울역"));

        verify(kakaoLocalClient).searchKeyword("서울역", 10);
        verifyNoMoreInteractions(kakaoLocalClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"destinations", "course"})
    @DisplayName("CASE 11, 12: 실제 여행 요청은 좌표를 다시 확인하고 부산 밖이면 FastAPI 호출 없이 차단한다")
    void tripEndpointsStillValidateCoordinates(String endpoint) throws Exception {
        when(kakaoLocalClient.searchRegionCode(any(), any())).thenReturn(region("서울특별시"));
        String destinationField = "course".equals(endpoint)
                ? "\"destinationId\": \"BUSAN_GWANGALLI\"," : "";

        mockMvc.perform(post("/api/v1/spontaneous-trips/" + endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  %s
                                  "startLocation": {"latitude": 37.5665, "longitude": 126.9780},
                                  "startAt": "2026-09-03T18:00:00+09:00",
                                  "returnBy": "2026-09-03T23:00:00+09:00",
                                  "transportMode": "CAR",
                                  "desiredThemes": ["SEA"]
                                }
                                """.formatted(destinationField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN"));

        verify(kakaoLocalClient).searchRegionCode(new BigDecimal("126.978"), new BigDecimal("37.5665"));
        verifyNoInteractions(fastApiSpontaneousClient);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "경기 수원시", "경상남도 김해시", "부산진구 부전동", "서울 중구 부산역점"})
    @DisplayName("주소가 없거나 부산 시도명이 아니면 행정구역 호출을 생략한다")
    void skipsAddressesWithoutBusanRegion(String address) throws Exception {
        when(kakaoLocalClient.searchKeyword("장소", 10)).thenReturn(new KakaoLocalSearchResponse(
                List.of(place("place", "장소", address))));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "장소"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(kakaoLocalClient).searchKeyword("장소", 10);
        verifyNoMoreInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("키워드 검색 결과가 없으면 행정구역을 호출하지 않는다")
    void emptyKeywordResultsReturnEmptyItems() throws Exception {
        when(kakaoLocalClient.searchKeyword("없는 장소", 1)).thenReturn(new KakaoLocalSearchResponse(null));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "없는 장소").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        verify(kakaoLocalClient).searchKeyword("없는 장소", 1);
        verifyNoMoreInteractions(kakaoLocalClient);
    }

    @Test
    @DisplayName("행정구역 조회 결과가 없으면 부산으로 인정하지 않는다")
    void emptyRegionResultsExcludeCandidate() throws Exception {
        when(kakaoLocalClient.searchKeyword("부산역", 10)).thenReturn(new KakaoLocalSearchResponse(
                List.of(place("busan", "부산역", "부산 동구"))));
        when(kakaoLocalClient.searchRegionCode(any(), any())).thenReturn(new KakaoLocalRegionCodeResponse(null));

        mockMvc.perform(get(SEARCH_PATH).param("keyword", "부산역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private KakaoLocalSearchResponse.Document place(String id, String name, String address) {
        return new KakaoLocalSearchResponse.Document(id, name, address,
                "129.0403", "35.1151", null, null);
    }

    private KakaoLocalRegionCodeResponse region(String name) {
        return new KakaoLocalRegionCodeResponse(List.of(new KakaoLocalRegionCodeResponse.Document(
                "H", name + " 테스트동", name, "테스트구", "테스트동", "", "0000000000", 129.0403, 35.1151)));
    }
}
