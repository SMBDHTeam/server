package com.server.place.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.external.tourapi.TourApiClient;
import com.server.external.tourapi.TourApiPlaceDetailResponse;
import com.server.external.tourapi.TourApiPlaceImageResponse;
import com.server.external.tourapi.TourApiPlaceIntroResponse;
import com.server.external.tourapi.TourApiPlaceListResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@DisplayName("TourAPI 장소 적재 서비스")
class TourApiPlaceIngestionServiceTest {

    private final TourApiClient tourApiClient = Mockito.mock(TourApiClient.class);
    private final TourApiPlaceWriter placeWriter = Mockito.mock(TourApiPlaceWriter.class);

    @Test
    @DisplayName("TourAPI 장소 목록과 상세 정보를 내부 저장 항목으로 변환해 저장한다")
    void ingestConfiguredSavesTourApiPlaces() {
        TourApiPlaceIngestionService service = createService(List.of("12"), 100, 1);
        when(tourApiClient.searchPlaces("6", "12", 1, 100))
                .thenReturn(new TourApiPlaceListResponse(1, List.of(new TourApiPlaceListResponse.Item(
                        "126508",
                        "12",
                        "이송도전망대",
                        "A01010100",
                        "부산 서구 암남동",
                        "129.047956",
                        "35.075519",
                        "https://example.com/image.jpg",
                        "{}"
                ))));
        when(tourApiClient.getCommonDetail("126508", "12"))
                .thenReturn(new TourApiPlaceDetailResponse("장소 설명", "https://example.com", "{\"overview\":\"장소 설명\"}"));
        when(tourApiClient.getIntro("126508", "12"))
                .thenReturn(new TourApiPlaceIntroResponse("09:00~18:00", "연중무휴", "무료", "주차 가능", true, "{}"));
        when(tourApiClient.getImages("126508"))
                .thenReturn(new TourApiPlaceImageResponse(List.of(new TourApiPlaceImageResponse.Item(
                        "https://example.com/image.jpg",
                        "https://example.com/thumb.jpg",
                        "Type1"
                ))));

        TourApiPlaceIngestionResult result = service.ingestConfigured();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        ArgumentCaptor<PlaceIngestionItem> captor = ArgumentCaptor.forClass(PlaceIngestionItem.class);
        verify(placeWriter).upsert(captor.capture());
        PlaceIngestionItem item = captor.getValue();
        assertThat(item.externalContentId()).isEqualTo("126508");
        assertThat(item.name()).isEqualTo("이송도전망대");
        assertThat(item.longitude()).isEqualByComparingTo("129.047956");
        assertThat(item.overview()).isEqualTo("장소 설명");
        assertThat(item.openingHoursText()).isEqualTo("09:00~18:00");
        assertThat(item.images()).hasSize(1);
    }

    @Test
    @DisplayName("필수 필드가 누락된 장소는 상세 호출 없이 건너뛴다")
    void ingestConfiguredSkipsInvalidPlaces() {
        TourApiPlaceIngestionService service = createService(List.of("12"), 100, 1);
        when(tourApiClient.searchPlaces("6", "12", 1, 100))
                .thenReturn(new TourApiPlaceListResponse(1, List.of(new TourApiPlaceListResponse.Item(
                        "126508",
                        "12",
                        "이송도전망대",
                        "A01010100",
                        "부산 서구 암남동",
                        "",
                        "35.075519",
                        null,
                        "{}"
                ))));

        TourApiPlaceIngestionResult result = service.ingestConfigured();

        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.saved()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(tourApiClient, never()).getCommonDetail("126508", "12");
        verify(placeWriter, never()).upsert(Mockito.any());
    }

    private TourApiPlaceIngestionService createService(List<String> contentTypeIds, int pageSize, int maxPages) {
        return new TourApiPlaceIngestionService(
                tourApiClient,
                placeWriter,
                new TourApiPlaceIngestionProperties(false, "6", contentTypeIds, pageSize, maxPages)
        );
    }
}
