package com.server.place.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.tourapi.TourApiClient;
import com.server.external.tourapi.TourApiPlaceDetailResponse;
import com.server.external.tourapi.TourApiPlaceImageResponse;
import com.server.external.tourapi.TourApiPlaceIntroResponse;
import com.server.external.tourapi.TourApiPlaceListResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@DisplayName("TourAPI 장소 증분 동기화 서비스")
class TourApiPlaceIngestionServiceTest {

    private final TourApiClient tourApiClient = Mockito.mock(TourApiClient.class);
    private final TourApiPlaceWriter placeWriter = Mockito.mock(TourApiPlaceWriter.class);
    private final TourApiIngestionLock ingestionLock = Mockito.mock(TourApiIngestionLock.class);
    private final TourApiDailyRequestQuota requestQuota = Mockito.mock(TourApiDailyRequestQuota.class);

    @BeforeEach
    void setUp() {
        when(ingestionLock.tryAcquire()).thenReturn(Optional.of(new TourApiIngestionLock.Lease("owner")));
        when(requestQuota.tryReserve(anyInt(), eq(900))).thenReturn(true);
    }

    @Test
    @DisplayName("신규 장소는 목록 발견 후 상세정보를 보강한다")
    void enrichesNewPlace() {
        TourApiPlaceIngestionService service = createService();
        stubList(validItem("20260713040000"));
        when(placeWriter.discover(any(), any())).thenReturn(candidate(true, true));
        stubEnrichment();

        TourApiPlaceIngestionResult result = service.ingestConfigured();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.discovered()).isEqualTo(1);
        assertThat(result.enriched()).isEqualTo(1);
        assertThat(result.apiRequests()).isEqualTo(4);
        ArgumentCaptor<PlaceDiscoveryItem> discovery = ArgumentCaptor.forClass(PlaceDiscoveryItem.class);
        verify(placeWriter).discover(discovery.capture(), any(LocalDateTime.class));
        assertThat(discovery.getValue().sourceModifiedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 13, 4, 0));
        assertThat(discovery.getValue().longitude()).isEqualByComparingTo("129.04795612");
        verify(placeWriter).completeEnrichment(eq("126508"), any(), any(LocalDateTime.class));
        verify(ingestionLock).release(any());
    }

    @Test
    @DisplayName("변경 없는 장소는 상세 API를 호출하지 않는다")
    void skipsUnchangedPlace() {
        TourApiPlaceIngestionService service = createService();
        stubList(validItem("20260713040000"));
        when(placeWriter.discover(any(), any())).thenReturn(candidate(false, false));

        TourApiPlaceIngestionResult result = service.ingestConfigured();

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.apiRequests()).isEqualTo(1);
        verify(tourApiClient, never()).getCommonDetail(any());
        verify(tourApiClient, never()).getIntro(any(), any());
        verify(tourApiClient, never()).getImages(any());
    }

    @Test
    @DisplayName("일일 호출 예산이 부족하면 상세 보강을 PENDING으로 남긴다")
    void leavesPendingWhenQuotaIsExhausted() {
        TourApiPlaceIngestionService service = createService();
        stubList(validItem("20260713040000"));
        when(placeWriter.discover(any(), any())).thenReturn(candidate(true, true));
        when(requestQuota.tryReserve(1, 900)).thenReturn(true);
        when(requestQuota.tryReserve(3, 900)).thenReturn(false);

        TourApiPlaceIngestionResult result = service.ingestConfigured();

        assertThat(result.pending()).isEqualTo(1);
        assertThat(result.enriched()).isZero();
        assertThat(result.apiRequests()).isEqualTo(1);
        verify(tourApiClient, never()).getCommonDetail(any());
    }

    @Test
    @DisplayName("상세 Provider 실패는 장소 실패 상태로 기록하고 전체 실행을 계속한다")
    void recordsProviderFailure() {
        TourApiPlaceIngestionService service = createService();
        stubList(validItem("20260713040000"));
        when(placeWriter.discover(any(), any())).thenReturn(candidate(true, true));
        when(tourApiClient.getCommonDetail("126508"))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));

        TourApiPlaceIngestionResult result = service.ingestConfigured();

        assertThat(result.failed()).isEqualTo(1);
        verify(placeWriter).markFailed(
                eq("126508"),
                eq("EXTERNAL_PROVIDER_UNAVAILABLE"),
                any(LocalDateTime.class)
        );
        verify(ingestionLock).release(any());
    }

    @Test
    @DisplayName("좌표 범위를 벗어난 장소는 상세 호출 없이 건너뛴다")
    void skipsInvalidCoordinates() {
        TourApiPlaceIngestionService service = createService();
        TourApiPlaceListResponse.Item invalid = new TourApiPlaceListResponse.Item(
                "126508", "12", "이송도전망대", "A01010100", "부산 서구", "181", "35",
                null, "20260713040000", "{}"
        );
        stubList(invalid);

        TourApiPlaceIngestionResult result = service.ingestConfigured();

        assertThat(result.skipped()).isEqualTo(1);
        verify(placeWriter, never()).discover(any(), any());
        verify(tourApiClient, never()).getCommonDetail(any());
    }

    @Test
    @DisplayName("다른 인스턴스가 적재 중이면 실행을 건너뛴다")
    void skipsWhenLockIsHeld() {
        when(ingestionLock.tryAcquire()).thenReturn(Optional.empty());

        TourApiPlaceIngestionResult result = createService().ingestConfigured();

        assertThat(result.lockSkipped()).isTrue();
        verify(tourApiClient, never()).searchPlaces(any(), any(), anyInt(), anyInt());
        verify(ingestionLock, never()).release(any());
    }

    private TourApiPlaceIngestionService createService() {
        return new TourApiPlaceIngestionService(
                tourApiClient,
                placeWriter,
                new TourApiPlaceIngestionProperties(false, "6", List.of("12"), 100, 1, 900),
                ingestionLock,
                requestQuota
        );
    }

    private void stubList(TourApiPlaceListResponse.Item item) {
        when(tourApiClient.searchPlaces("6", "12", 1, 100))
                .thenReturn(new TourApiPlaceListResponse(1, List.of(item)));
    }

    private TourApiPlaceListResponse.Item validItem(String modifiedTime) {
        return new TourApiPlaceListResponse.Item(
                "126508",
                "12",
                "이송도전망대",
                "A01010100",
                "부산 서구 암남동",
                "129.0479561234",
                "35.0755191234",
                "https://example.com/image.jpg",
                modifiedTime,
                "{}"
        );
    }

    private TourApiPlaceWriter.PlaceSyncCandidate candidate(boolean required, boolean created) {
        return new TourApiPlaceWriter.PlaceSyncCandidate("126508", "12", required, created);
    }

    private void stubEnrichment() {
        when(tourApiClient.getCommonDetail("126508"))
                .thenReturn(new TourApiPlaceDetailResponse("장소 설명", "https://example.com", "{}"));
        when(tourApiClient.getIntro("126508", "12"))
                .thenReturn(new TourApiPlaceIntroResponse(
                        "09:00~18:00", "연중무휴", "무료", "주차 가능", true, "{}"
                ));
        when(tourApiClient.getImages("126508"))
                .thenReturn(new TourApiPlaceImageResponse(List.of(new TourApiPlaceImageResponse.Item(
                        "https://example.com/image.jpg",
                        "https://example.com/thumb.jpg",
                        "Type1"
                ))));
    }
}
