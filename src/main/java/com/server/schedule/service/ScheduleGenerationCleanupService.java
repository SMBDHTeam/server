package com.server.schedule.service;

import com.server.schedule.domain.SchedulePreview;
import com.server.schedule.repository.ScheduleCreationRequestRepository;
import com.server.schedule.repository.SchedulePreviewRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleGenerationCleanupService {

    private final ScheduleCreationRequestRepository creationRequestRepository;
    private final SchedulePreviewRepository previewRepository;

    public ScheduleGenerationCleanupService(
            ScheduleCreationRequestRepository creationRequestRepository,
            SchedulePreviewRepository previewRepository
    ) {
        this.creationRequestRepository = creationRequestRepository;
        this.previewRepository = previewRepository;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredRecords() {
        OffsetDateTime now = OffsetDateTime.now();
        creationRequestRepository.deleteByExpiresAtBefore(now);
        List<SchedulePreview> expired = previewRepository.findByExpiresAtBeforeAndStatusNot(
                now.minusHours(24), "CONSUMED");
        previewRepository.deleteAll(expired);
    }
}
