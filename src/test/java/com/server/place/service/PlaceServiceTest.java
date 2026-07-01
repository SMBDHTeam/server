package com.server.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.domain.PlaceDetail;
import com.server.place.domain.PlaceImage;
import com.server.place.domain.PlaceOperatingInfo;
import com.server.place.dto.PlaceDetailResponse;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("장소 서비스")
class PlaceServiceTest {

    private final PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
    private final PlaceService placeService = new PlaceService(placeRepository);

    @Test
    @DisplayName("키워드로 내부 DB 장소를 검색한다")
    void searchByKeywordReturnsPlaces() {
        Place place = place(101L, "126508", "이송도전망대", "129.047956", "35.075519");
        when(placeRepository.findByNameContainingIgnoreCaseOrderByNameAsc("전망대"))
                .thenReturn(List.of(place));

        PlaceSearchResponse response = placeService.search(" 전망대 ", null, null, null);

        assertThat(response.items()).hasSize(1);
        PlaceSearchResponse.Item item = response.items().get(0);
        assertThat(item.id()).isEqualTo(101L);
        assertThat(item.externalContentId()).isEqualTo("126508");
        assertThat(item.name()).isEqualTo("이송도전망대");
        assertThat(item.distanceMeters()).isNull();
        verify(placeRepository).findByNameContainingIgnoreCaseOrderByNameAsc("전망대");
    }

    @Test
    @DisplayName("좌표 기준 반경 안의 장소를 거리순으로 검색한다")
    void searchByLocationReturnsPlacesWithinRadiusOrderedByDistance() {
        Place nearPlace = place(101L, "126508", "부산역", "129.040300", "35.115100");
        Place farPlace = place(102L, "999999", "해운대", "129.158700", "35.158700");
        when(placeRepository.findAll()).thenReturn(List.of(farPlace, nearPlace));

        PlaceSearchResponse response = placeService.search(
                null,
                new BigDecimal("129.040300"),
                new BigDecimal("35.115100"),
                1000
        );

        assertThat(response.items()).hasSize(1);
        PlaceSearchResponse.Item item = response.items().get(0);
        assertThat(item.id()).isEqualTo(101L);
        assertThat(item.name()).isEqualTo("부산역");
        assertThat(item.distanceMeters()).isZero();
    }

    @Test
    @DisplayName("검색 조건이 없거나 충돌하면 비즈니스 예외를 던진다")
    void invalidSearchConditionThrowsBusinessException() {
        assertThatThrownBy(() -> placeService.search(null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);

        assertThatThrownBy(() -> placeService.search("부산", new BigDecimal("129.0"), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);

        assertThatThrownBy(() -> placeService.search(null, new BigDecimal("129.0"), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);
    }

    @Test
    @DisplayName("장소 상세 정보를 명세 응답으로 변환한다")
    void getDetailReturnsPlaceDetail() {
        Place place = place(101L, "126508", "이송도전망대", "129.047956", "35.075519");
        new PlaceDetail(place, "장소 설명", "https://example.com", "{}");
        new PlaceOperatingInfo(place, "09:00~18:00", "연중무휴", "무료", "주차 가능", true, "{}");
        new PlaceImage(place, "https://example.com/image.jpg", "https://example.com/thumbnail.jpg", "Type1", 1);
        when(placeRepository.findById(101L)).thenReturn(Optional.of(place));

        PlaceDetailResponse response = placeService.getDetail(101L);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.externalContentId()).isEqualTo("126508");
        assertThat(response.contentTypeId()).isEqualTo("12");
        assertThat(response.overview()).isEqualTo("장소 설명");
        assertThat(response.operatingInfo().openingHoursText()).isEqualTo("09:00~18:00");
        assertThat(response.operatingInfo().requiresManualCheck()).isTrue();
        assertThat(response.images()).hasSize(1);
        assertThat(response.images().get(0).url()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    @DisplayName("장소가 없으면 장소 없음 예외를 던진다")
    void missingPlaceThrowsPlaceNotFound() {
        when(placeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeService.getDetail(404L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }

    private Place place(Long id, String externalContentId, String name, String longitude, String latitude) {
        Place place = new Place(
                "TOUR_API",
                externalContentId,
                "12",
                name,
                "관광지",
                "부산 서구 암남동",
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                "https://example.com/image.jpg"
        );
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }
}
