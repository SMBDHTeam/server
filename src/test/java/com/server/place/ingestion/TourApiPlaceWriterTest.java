package com.server.place.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.server.place.domain.Place;
import com.server.place.domain.PlaceImage;
import com.server.place.domain.PlaceIngestionStatus;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("TourAPI 장소 저장 Writer")
class TourApiPlaceWriterTest {

    private final PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
    private final TourApiPlaceWriter writer = new TourApiPlaceWriter(placeRepository);
    private final AtomicReference<Place> storedPlace = new AtomicReference<>();

    @BeforeEach
    void setUpRepository() {
        when(placeRepository.findBySourceAndExternalContentId("TOUR_API", "126508"))
                .thenAnswer(ignored -> Optional.ofNullable(storedPlace.get()));
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            storedPlace.set(place);
            return place;
        });
    }

    @Test
    @DisplayName("신규 장소를 PENDING으로 발견하고 상세 보강 후 SYNCED로 저장한다")
    void discoversAndEnrichesNewPlace() {
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 13, 4, 0);

        TourApiPlaceWriter.PlaceSyncCandidate candidate = writer.discover(discovery(modifiedAt), modifiedAt);
        writer.completeEnrichment("126508", enrichment(), modifiedAt.plusMinutes(1));

        Place saved = storedPlace.get();
        assertThat(candidate.created()).isTrue();
        assertThat(candidate.enrichmentRequired()).isTrue();
        assertThat(saved.getIngestionStatus()).isEqualTo(PlaceIngestionStatus.SYNCED);
        assertThat(saved.getSourceModifiedAt()).isEqualTo(modifiedAt);
        assertThat(saved.getDetail().getOverview()).isEqualTo("장소 설명");
        assertThat(saved.getOperatingInfo().getParkingText()).isEqualTo("주차 가능");
        assertThat(saved.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("수정시각과 기본정보가 같으면 상세 보강이 필요하지 않다")
    void unchangedPlaceDoesNotRequireEnrichment() {
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 13, 4, 0);
        writer.discover(discovery(modifiedAt), modifiedAt);
        writer.completeEnrichment("126508", enrichment(), modifiedAt.plusMinutes(1));

        TourApiPlaceWriter.PlaceSyncCandidate candidate = writer.discover(
                discovery(modifiedAt),
                modifiedAt.plusDays(1)
        );

        assertThat(candidate.created()).isFalse();
        assertThat(candidate.enrichmentRequired()).isFalse();
        assertThat(storedPlace.get().getLastSeenAt()).isEqualTo(modifiedAt.plusDays(1));
    }

    @Test
    @DisplayName("수정시각이 변경되면 기존 장소를 다시 PENDING으로 전환한다")
    void changedPlaceRequiresEnrichment() {
        LocalDateTime firstModifiedAt = LocalDateTime.of(2026, 7, 13, 4, 0);
        writer.discover(discovery(firstModifiedAt), firstModifiedAt);
        writer.completeEnrichment("126508", enrichment(), firstModifiedAt.plusMinutes(1));

        TourApiPlaceWriter.PlaceSyncCandidate candidate = writer.discover(
                discovery(firstModifiedAt.plusDays(1)),
                firstModifiedAt.plusDays(1)
        );

        assertThat(candidate.enrichmentRequired()).isTrue();
        assertThat(storedPlace.get().getIngestionStatus()).isEqualTo(PlaceIngestionStatus.PENDING);
    }

    @Test
    @DisplayName("동기화 실패는 기존 상세정보를 유지하고 재시도 상태만 기록한다")
    void failureKeepsExistingData() {
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 13, 4, 0);
        writer.discover(discovery(modifiedAt), modifiedAt);
        writer.completeEnrichment("126508", enrichment(), modifiedAt.plusMinutes(1));

        writer.markFailed("126508", "EXTERNAL_PROVIDER_UNAVAILABLE", modifiedAt.plusDays(1));

        Place saved = storedPlace.get();
        assertThat(saved.getIngestionStatus()).isEqualTo(PlaceIngestionStatus.FAILED);
        assertThat(saved.getIngestionRetryCount()).isEqualTo(1);
        assertThat(saved.getIngestionLastError()).isEqualTo("EXTERNAL_PROVIDER_UNAVAILABLE");
        assertThat(saved.getIngestionNextRetryAt()).isEqualTo(modifiedAt.plusDays(2));
        assertThat(saved.getDetail().getOverview()).isEqualTo("장소 설명");
        assertThat(saved.getImages()).hasSize(1);

        TourApiPlaceWriter.PlaceSyncCandidate beforeRetry = writer.discover(
                discovery(modifiedAt),
                modifiedAt.plusDays(1).plusHours(12)
        );
        TourApiPlaceWriter.PlaceSyncCandidate retryDue = writer.discover(
                discovery(modifiedAt),
                modifiedAt.plusDays(2)
        );

        assertThat(beforeRetry.enrichmentRequired()).isFalse();
        assertThat(retryDue.enrichmentRequired()).isTrue();
    }

    @Test
    @DisplayName("동일한 이미지 목록은 엔티티를 교체하지 않는다")
    void identicalImagesAreNotReplaced() {
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 13, 4, 0);
        writer.discover(discovery(modifiedAt), modifiedAt);
        writer.completeEnrichment("126508", enrichment(), modifiedAt.plusMinutes(1));
        PlaceImage existingImage = storedPlace.get().getImages().get(0);

        writer.completeEnrichment("126508", enrichment(), modifiedAt.plusDays(1));

        assertThat(storedPlace.get().getImages().get(0)).isSameAs(existingImage);
    }

    private PlaceDiscoveryItem discovery(LocalDateTime modifiedAt) {
        return new PlaceDiscoveryItem(
                "126508",
                "12",
                "이송도전망대",
                "A01010100",
                "부산 서구 암남동",
                new BigDecimal("129.047956"),
                new BigDecimal("35.075519"),
                "https://example.com/primary.jpg",
                modifiedAt
        );
    }

    private PlaceEnrichmentItem enrichment() {
        return new PlaceEnrichmentItem(
                "장소 설명",
                "https://example.com",
                "{}",
                "09:00~18:00",
                "연중무휴",
                "무료",
                "주차 가능",
                true,
                "{}",
                List.of(new PlaceEnrichmentItem.Image(
                        "https://example.com/image.jpg",
                        "https://example.com/thumb.jpg",
                        "Type1"
                ))
        );
    }
}
