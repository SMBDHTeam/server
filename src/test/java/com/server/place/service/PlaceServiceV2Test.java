package com.server.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.place.domain.Place;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.repository.PlaceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("V2 통합 장소 검색")
class PlaceServiceV2Test {

    @Test
    @DisplayName("Kakao 검색 후보는 반환만 하고 선택 전에는 저장하지 않는다")
    void externalCandidatesAreNotPersistedDuringSearch() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
        when(placeRepository.findByNameContainingIgnoreCaseOrderByNameAsc("새 장소"))
                .thenReturn(List.of());
        when(placeRepository.findAll()).thenReturn(List.of());
        when(kakaoLocalClient.searchKeyword("새 장소", 5)).thenReturn(new KakaoLocalSearchResponse(List.of(
                new KakaoLocalSearchResponse.Document(
                        "kakao-1", "새 장소", "부산광역시", "카페", "129.12", "35.15", "", "https://place")
        )));
        PlaceService service = new PlaceService(placeRepository, kakaoLocalClient);

        PlaceSearchResponse response = service.search("새 장소", null, null, null, "ALL", 5);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.placeId()).isNull();
            assertThat(item.source()).isEqualTo("KAKAO_LOCAL");
            assertThat(item.externalId()).isEqualTo("kakao-1");
            assertThat(item.resolved()).isFalse();
        });
        verify(placeRepository, never()).save(org.mockito.ArgumentMatchers.any(Place.class));
    }
}
