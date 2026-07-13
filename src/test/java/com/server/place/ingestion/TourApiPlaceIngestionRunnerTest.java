package com.server.place.ingestion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.ApplicationRunner;

@DisplayName("TourAPI 장소 적재 Runner")
class TourApiPlaceIngestionRunnerTest {

    private final TourApiPlaceIngestionService ingestionService = Mockito.mock(TourApiPlaceIngestionService.class);
    private final TourApiPlaceIngestionRunner runnerConfig = new TourApiPlaceIngestionRunner();

    @Test
    @DisplayName("적재 성공 결과를 로그로 남기고 종료한다")
    void runnerLogsSuccessfulIngestionResult() throws Exception {
        when(ingestionService.ingestConfigured())
                .thenReturn(new TourApiPlaceIngestionResult(1, 1, 1, 0, 0, 0, 0, 4, false));
        ApplicationRunner runner = runnerConfig.ingestTourApiPlaces(ingestionService);

        runner.run(null);

        verify(ingestionService).ingestConfigured();
    }

    @Test
    @DisplayName("적재 실패는 서버 기동을 막지 않도록 삼킨다")
    void runnerDoesNotFailStartupWhenIngestionFails() {
        when(ingestionService.ingestConfigured())
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));
        ApplicationRunner runner = runnerConfig.ingestTourApiPlaces(ingestionService);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        verify(ingestionService).ingestConfigured();
    }
}
