package com.server.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.facility.dto.NearbyFacilityResponse;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("주변 편의시설 서비스")
class NearbyFacilityServiceTest {

    private final PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
    private final KakaoLocalClient kakaoLocalClient = Mockito.mock(KakaoLocalClient.class);
    private final NearbyFacilityService nearbyFacilityService = new NearbyFacilityService(
            placeRepository,
            kakaoLocalClient
    );

    @Test
    @DisplayName("장소 좌표 기준으로 주변 편의점을 조회한다")
    void searchReturnsConvenienceStoresNearPlace() {
        Place place = new Place(
                "TOUR_API",
                "126508",
                "12",
                "이송도전망대",
                "관광지",
                "부산 서구 암남동",
                new BigDecimal("129.047956"),
                new BigDecimal("35.075519"),
                null
        );
        when(placeRepository.findById(101L)).thenReturn(Optional.of(place));
        when(kakaoLocalClient.searchConvenienceStores(
                new BigDecimal("129.047956"),
                new BigDecimal("35.075519"),
                1000
        )).thenReturn(new KakaoLocalSearchResponse(List.of(new KakaoLocalSearchResponse.Document(
                "kakao-place-id",
                "CU 부산역점",
                "부산 동구 중앙대로",
                "129.041",
                "35.115",
                "120",
                "https://place.map.kakao.com/1"
        ))));

        NearbyFacilityResponse response = nearbyFacilityService.search(101L, "CONVENIENCE_STORE", 1000);

        assertThat(response.items()).hasSize(1);
        NearbyFacilityResponse.Item item = response.items().get(0);
        assertThat(item.externalId()).isEqualTo("kakao-place-id");
        assertThat(item.type()).isEqualTo("CONVENIENCE_STORE");
        assertThat(item.name()).isEqualTo("CU 부산역점");
        assertThat(item.distanceMeters()).isEqualTo(120);
        assertThat(item.source()).isEqualTo("KAKAO_LOCAL");
    }

    @Test
    @DisplayName("지원하지 않는 유형이면 장소 조회 전에 비즈니스 예외를 던진다")
    void unsupportedTypeThrowsBusinessExceptionBeforePlaceLookup() {
        assertThatThrownBy(() -> nearbyFacilityService.search(101L, "ATM", 1000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FACILITY_TYPE_NOT_SUPPORTED);

        verify(placeRepository, never()).findById(101L);
    }

    @Test
    @DisplayName("기준 장소가 없으면 장소 없음 예외를 던진다")
    void missingPlaceThrowsPlaceNotFound() {
        when(placeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nearbyFacilityService.search(404L, "CONVENIENCE_STORE", 1000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }
}
