package com.server.place.ingestion;

import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatCode;

public class TourApiPlaceIngestionSchedulerTest {
    private final TourApiPlaceIngestionService ingestionService = mock(TourApiPlaceIngestionService.class);
    private final TourApiPlaceIngestionScheduler scheduler = new TourApiPlaceIngestionScheduler(ingestionService);

    @Test
    void 스케줄이_실행되면_적재_서비스를_호출한다() {
        given(ingestionService.ingestConfigured())
                .willReturn(new TourApiPlaceIngestionResult(10, 8, 2));

        scheduler.ingestDaily();

        then(ingestionService).should().ingestConfigured();
    }

    @Test
    void 적재가_실패해도_예외를_밖으로_던지지_않는다() {
        given(ingestionService.ingestConfigured())
                .willThrow(new RuntimeException("TourAPI 응답 실패"));

        assertThatCode(scheduler::ingestDaily).doesNotThrowAnyException();
    }
}

