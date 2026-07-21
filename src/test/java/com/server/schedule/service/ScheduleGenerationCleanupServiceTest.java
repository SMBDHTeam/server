package com.server.schedule.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.schedule.repository.ScheduleCreationRequestRepository;
import com.server.schedule.repository.SchedulePreviewRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("일정 생성 임시 데이터 정리")
class ScheduleGenerationCleanupServiceTest {

    @Test
    @DisplayName("만료된 멱등성 요청과 24시간 지난 미소비 Preview를 정리한다")
    void deletesExpiredGenerationRecords() {
        ScheduleCreationRequestRepository requestRepository = mock(ScheduleCreationRequestRepository.class);
        SchedulePreviewRepository previewRepository = mock(SchedulePreviewRepository.class);
        when(previewRepository.findByExpiresAtBeforeAndStatusNot(any(OffsetDateTime.class), eq("CONSUMED")))
                .thenReturn(List.of());
        ScheduleGenerationCleanupService service = new ScheduleGenerationCleanupService(
                requestRepository, previewRepository);

        service.deleteExpiredRecords();

        verify(requestRepository).deleteByExpiresAtBefore(any(OffsetDateTime.class));
        verify(previewRepository).findByExpiresAtBeforeAndStatusNot(any(OffsetDateTime.class), eq("CONSUMED"));
        verify(previewRepository).deleteAll(List.of());
    }
}
