package com.server.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.place.domain.Place;
import com.server.place.dto.PlaceKakaoLinkResponse;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("장소 카카오맵 주소")
class PlaceKakaoLinkServiceTest {

    private static final BigDecimal LON = new BigDecimal("129.05850000");
    private static final BigDecimal LAT = new BigDecimal("35.10180000");

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
    private final PlaceKakaoLinkService service = new PlaceKakaoLinkService(placeRepository, kakaoLocalClient);

    @Test
    @DisplayName("카카오에서 가져온 장소는 검색하지 않고 원본 ID 로 상세 페이지를 준다")
    void kakaoSourceUsesExternalId() {
        given(1L, new Place("KAKAO_LOCAL", "7913306", null, "해운대해수욕장", null, "부산 영도구", LON, LAT, null));

        PlaceKakaoLinkResponse response = service.getLink(1L);

        assertThat(response.matched()).isTrue();
        assertThat(response.kakaoPlaceId()).isEqualTo("7913306");
        assertThat(response.url()).isEqualTo("https://place.map.kakao.com/7913306");
        verify(kakaoLocalClient, never()).searchKeywordNear(anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("다른 출처는 좌표 주변에서 표기만 다른 같은 이름을 찾아 잇는다")
    void otherSourceMatchesByNameNearby() {
        given(2L, tourPlace("국제시장 먹자골목"));
        when(kakaoLocalClient.searchKeywordNear(
                eq("국제시장 먹자골목"), eq(LON), eq(LAT), eq(PlaceKakaoLinkService.MATCH_RADIUS_METERS), anyInt()))
                .thenReturn(documents(
                        document("111", "국제시장 주차장"),
                        document("222", "국제시장먹자골목")));

        PlaceKakaoLinkResponse response = service.getLink(2L);

        assertThat(response.matched()).isTrue();
        assertThat(response.kakaoPlaceId()).isEqualTo("222");
        assertThat(response.url()).isEqualTo("https://place.map.kakao.com/222");
    }

    @Test
    @DisplayName("이름을 딴 더 가까운 가게는 건너뛰고 이름이 같은 장소와 잇는다")
    void nearerShopWithBorrowedNameIsSkipped() {
        given(9L, tourPlace("부산 송도해수욕장"));
        when(kakaoLocalClient.searchKeywordNear(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(documents(
                        document("17126984", "탐앤탐스 부산송도해수욕장점"),
                        document("458586004", "송도해수욕장 현인광장"),
                        document("25041637", "송도해수욕장")));

        PlaceKakaoLinkResponse response = service.getLink(9L);

        assertThat(response.kakaoPlaceId()).isEqualTo("25041637");
    }

    @Test
    @DisplayName("괄호 설명은 빼고 검색해, 카카오의 짧은 이름과 잇는다")
    void parentheticalIsStrippedFromKeyword() {
        given(8L, tourPlace("동래온천길(온천천 카페거리)"));
        when(kakaoLocalClient.searchKeywordNear(eq("동래온천길"), any(), any(), anyInt(), anyInt()))
                .thenReturn(documents(document("888", "동래온천길")));

        PlaceKakaoLinkResponse response = service.getLink(8L);

        assertThat(response.kakaoPlaceId()).isEqualTo("888");
        assertThat(PlaceKakaoLinkService.searchUrl("송도반도 (부산 국가지질공원)"))
                .endsWith("?q=%EB%B6%80%EC%82%B0%20%EC%86%A1%EB%8F%84%EB%B0%98%EB%8F%84");
    }

    @Test
    @DisplayName("주변에 같은 이름이 없으면 부산을 붙인 이름 검색 페이지를 준다")
    void noMatchFallsBackToSearchPage() {
        given(3L, tourPlace("감천사"));
        when(kakaoLocalClient.searchKeywordNear(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(documents(document("333", "감천동 주민센터")));

        PlaceKakaoLinkResponse response = service.getLink(3L);

        assertThat(response.matched()).isFalse();
        assertThat(response.kakaoPlaceId()).isNull();
        assertThat(response.url())
                .isEqualTo("https://m.map.kakao.com/actions/searchView?q=%EB%B6%80%EC%82%B0%20%EA%B0%90%EC%B2%9C%EC%82%AC");
    }

    @Test
    @DisplayName("카카오 호출이 실패해도 오류 대신 이름 검색 페이지를 준다")
    void providerFailureFallsBackToSearchPage() {
        given(4L, tourPlace("부산시민공원"));
        when(kakaoLocalClient.searchKeywordNear(anyString(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));

        PlaceKakaoLinkResponse response = service.getLink(4L);

        assertThat(response.matched()).isFalse();
        // 이름에 이미 부산이 있으면 한 번 더 붙이지 않는다.
        assertThat(response.url()).endsWith("?q=%EB%B6%80%EC%82%B0%EC%8B%9C%EB%AF%BC%EA%B3%B5%EC%9B%90");
    }

    @Test
    @DisplayName("좌표가 없으면 검색하지 않고 이름 검색 페이지를 준다")
    void missingCoordinatesSkipLookup() {
        given(5L, new Place("NAVER_LOCAL", "n-1", null, "좌표없는 식당", null, null, null, null, null));

        PlaceKakaoLinkResponse response = service.getLink(5L);

        assertThat(response.matched()).isFalse();
        verify(kakaoLocalClient, never()).searchKeywordNear(anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("없거나 가린 장소는 장소 상세와 같이 PLACE_NOT_FOUND 다")
    void missingOrHiddenPlaceIsNotFound() {
        when(placeRepository.findById(6L)).thenReturn(Optional.empty());
        Place hidden = tourPlace("가린 장소");
        hidden.hide("테스트");
        given(7L, hidden);

        assertThatThrownBy(() -> service.getLink(6L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND));
        assertThatThrownBy(() -> service.getLink(7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND));
    }

    @Test
    @DisplayName("이름 비교는 공백·괄호·앞의 부산을 무시하고, 한쪽이 품는 것은 같게 보지 않는다")
    void sameNameRules() {
        assertThat(PlaceKakaoLinkService.sameName("감천사(부산)", "감천사")).isTrue();
        assertThat(PlaceKakaoLinkService.sameName("부산 송도해상케이블카", "송도해상케이블카")).isTrue();
        assertThat(PlaceKakaoLinkService.sameName("송도 구름산책로", "송도구름산책로")).isTrue();
        assertThat(PlaceKakaoLinkService.sameName("부산", "부산")).isTrue();
        assertThat(PlaceKakaoLinkService.sameName("해운대", "해운대 빛축제")).isFalse();
        assertThat(PlaceKakaoLinkService.sameName("광안리해수욕장", "플랜비스튜디오 광안리해수욕장점")).isFalse();
        assertThat(PlaceKakaoLinkService.sameName("송도 구름산책로", "송도구름산책로 송도365")).isFalse();
    }

    private void given(Long id, Place place) {
        when(placeRepository.findById(id)).thenReturn(Optional.of(place));
    }

    private static Place tourPlace(String name) {
        return new Place("TOUR_API", "tour-" + name, "12", name, "A0101", "부산 중구", LON, LAT, null);
    }

    private static KakaoLocalSearchResponse documents(KakaoLocalSearchResponse.Document... documents) {
        return new KakaoLocalSearchResponse(List.of(documents));
    }

    private static KakaoLocalSearchResponse.Document document(String id, String name) {
        return new KakaoLocalSearchResponse.Document(id, name, "부산 중구", "여행", "129.0585", "35.1018", "10",
                "http://place.map.kakao.com/" + id);
    }
}
