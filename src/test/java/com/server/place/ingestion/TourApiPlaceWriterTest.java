package com.server.place.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.place.domain.Place;
import com.server.place.domain.PlaceImage;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@DisplayName("TourAPI 장소 저장 Writer")
class TourApiPlaceWriterTest {

    private final PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
    private final TourApiPlaceWriter writer = new TourApiPlaceWriter(placeRepository);

    @Test
    @DisplayName("신규 장소와 상세·운영·이미지를 저장한다")
    void upsertCreatesNewPlace() {
        when(placeRepository.findBySourceAndExternalContentId("TOUR_API", "126508"))
                .thenReturn(Optional.empty());

        writer.upsert(item("126508", "이송도전망대", "129.047956", "35.075519"));

        ArgumentCaptor<Place> captor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(captor.capture());
        Place saved = captor.getValue();
        assertThat(saved.getExternalContentId()).isEqualTo("126508");
        assertThat(saved.getName()).isEqualTo("이송도전망대");
        assertThat(saved.getDetail().getOverview()).isEqualTo("장소 설명");
        assertThat(saved.getOperatingInfo().getParkingText()).isEqualTo("주차 가능");
        assertThat(saved.getImages()).hasSize(1);
        assertThat(saved.getImages().get(0).getUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    @DisplayName("기존 장소는 같은 externalContentId 기준으로 갱신한다")
    void upsertUpdatesExistingPlace() {
        Place existing = new Place(
                "TOUR_API",
                "126508",
                "12",
                "기존 이름",
                "관광지",
                "기존 주소",
                new BigDecimal("129.000000"),
                new BigDecimal("35.000000"),
                null
        );
        new PlaceImage(existing, "https://example.com/old.jpg", null, null, 1);
        when(placeRepository.findBySourceAndExternalContentId("TOUR_API", "126508"))
                .thenReturn(Optional.of(existing));

        writer.upsert(item("126508", "새 이름", "129.047956", "35.075519"));

        assertThat(existing.getName()).isEqualTo("새 이름");
        assertThat(existing.getLongitude()).isEqualByComparingTo("129.047956");
        assertThat(existing.getImages()).hasSize(1);
        assertThat(existing.getImages().get(0).getUrl()).isEqualTo("https://example.com/image.jpg");
        verify(placeRepository).save(existing);
    }

    private PlaceIngestionItem item(String externalContentId, String name, String longitude, String latitude) {
        return new PlaceIngestionItem(
                externalContentId,
                "12",
                name,
                "A01010100",
                "부산 서구 암남동",
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                "https://example.com/primary.jpg",
                "장소 설명",
                "https://example.com",
                "{}",
                "09:00~18:00",
                "연중무휴",
                "무료",
                "주차 가능",
                true,
                "{}",
                List.of(new PlaceIngestionItem.Image(
                        "https://example.com/image.jpg",
                        "https://example.com/thumb.jpg",
                        "Type1"
                ))
        );
    }
}
