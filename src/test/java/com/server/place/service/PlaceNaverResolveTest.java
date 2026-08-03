package com.server.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.dto.PlaceResolveRequest;
import com.server.place.dto.PlaceResolveResponse;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("네이버 장소 등록")
class PlaceNaverResolveTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceService service = new PlaceService(placeRepository);

    @Test
    @DisplayName("우리 DB에 없는 네이버 장소는 새로 등록하고 카테고리로 콘텐츠 유형을 채운다")
    void registersANewNaverPlaceWithADerivedContentType() {
        when(placeRepository.findBySourceAndExternalContentId("NAVER_LOCAL", "1291600000-351700000"))
                .thenReturn(Optional.empty());
        when(placeRepository.findAll()).thenReturn(List.of());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            ReflectionTestUtils.setField(place, "id", 900L);
            return place;
        });

        PlaceResolveResponse response = service.resolve(new PlaceResolveRequest(
                "NAVER_LOCAL", "1291600000-351700000", "몽실종가돼지국밥",
                "음식점>한식>육류,고기요리", "부산 해운대구",
                new BigDecimal("129.16"), new BigDecimal("35.17"), null));

        assertThat(response.placeId()).isEqualTo(900L);
        assertThat(response.source()).isEqualTo("NAVER_LOCAL");
        verify(placeRepository).save(any(Place.class));
    }

    @Test
    @DisplayName("좌표와 이름이 같은 기존 장소가 있으면 새로 만들지 않고 그 장소를 돌려준다")
    void linksToAnAlreadyIngestedPlaceInsteadOfDuplicatingIt() {
        Place ingested = ingestedPlace();
        when(placeRepository.findBySourceAndExternalContentId("NAVER_LOCAL", "1291598546-351585232"))
                .thenReturn(Optional.empty());
        when(placeRepository.findAll()).thenReturn(List.of(ingested));

        PlaceResolveResponse response = service.resolve(new PlaceResolveRequest(
                "NAVER_LOCAL", "1291598546-351585232", "해운대 해수욕장",
                "여행,명소>관광,명소>해수욕장", "부산 해운대구 우동",
                new BigDecimal("129.1598546"), new BigDecimal("35.1585232"), null));

        assertThat(response.placeId()).isEqualTo(470L);
        assertThat(response.source()).isEqualTo("TOUR_API");
        verify(placeRepository, never()).save(any(Place.class));
    }

    @Test
    @DisplayName("기존 장소에 연결할 때 적재해 둔 이름과 좌표를 덮어쓰지 않는다")
    void keepsTheIngestedPlaceUntouchedWhenLinking() {
        Place ingested = ingestedPlace();
        when(placeRepository.findBySourceAndExternalContentId("NAVER_LOCAL", "1291598546-351585232"))
                .thenReturn(Optional.empty());
        when(placeRepository.findAll()).thenReturn(List.of(ingested));

        service.resolve(new PlaceResolveRequest(
                "NAVER_LOCAL", "1291598546-351585232", "해운대 해수욕장",
                "여행,명소>관광,명소>해수욕장", "네이버 주소",
                new BigDecimal("129.1598546"), new BigDecimal("35.1585232"), null));

        assertThat(ingested.getName()).isEqualTo("해운대해수욕장");
        assertThat(ingested.getAddress()).isEqualTo("부산광역시 해운대구 우동");
        assertThat(ingested.getCategory()).isEqualTo("A01011200");
        assertThat(ingested.getContentTypeId()).isEqualTo("12");
    }

    @Test
    @DisplayName("멀리 떨어진 동명 장소는 다른 장소로 보고 새로 등록한다")
    void doesNotLinkPlacesThatOnlyShareAName() {
        when(placeRepository.findBySourceAndExternalContentId("NAVER_LOCAL", "1270000000-375000000"))
                .thenReturn(Optional.empty());
        when(placeRepository.findAll()).thenReturn(List.of(ingestedPlace()));
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            ReflectionTestUtils.setField(place, "id", 901L);
            return place;
        });

        PlaceResolveResponse response = service.resolve(new PlaceResolveRequest(
                "NAVER_LOCAL", "1270000000-375000000", "해운대해수욕장",
                "여행,명소", "서울 어딘가",
                new BigDecimal("127.0"), new BigDecimal("37.5"), null));

        assertThat(response.placeId()).isEqualTo(901L);
        assertThat(response.source()).isEqualTo("NAVER_LOCAL");
    }

    @Test
    @DisplayName("지원하지 않는 출처는 거부한다")
    void rejectsAnUnsupportedSource() {
        assertThatThrownBy(() -> service.resolve(new PlaceResolveRequest(
                "GOOGLE_PLACES", "abc", "어떤 장소", null, null,
                new BigDecimal("129.0"), new BigDecimal("35.1"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_EXTERNAL_PLACE);
    }

    private Place ingestedPlace() {
        Place place = new Place(
                "TOUR_API", "264597", "12", "해운대해수욕장", "A01011200",
                "부산광역시 해운대구 우동",
                new BigDecimal("129.1600000"), new BigDecimal("35.1586000"), null);
        ReflectionTestUtils.setField(place, "id", 470L);
        return place;
    }
}
