package com.server.schedule.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.PreviewAlreadyConsumedException;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleCreationRequest;
import com.server.schedule.domain.SchedulePreview;
import com.server.schedule.repository.ScheduleCreationRequestRepository;
import com.server.schedule.repository.SchedulePreviewRepository;
import com.server.schedule.repository.ScheduleRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleCreationPersistenceService {

    private final ScheduleCreationRequestRepository requestRepository;
    private final SchedulePreviewRepository previewRepository;
    private final ScheduleRepository scheduleRepository;

    public ScheduleCreationPersistenceService(
            ScheduleCreationRequestRepository requestRepository,
            SchedulePreviewRepository previewRepository,
            ScheduleRepository scheduleRepository
    ) {
        this.requestRepository = requestRepository;
        this.previewRepository = previewRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ExistingRequest> find(String key) {
        return requestRepository.findByIdempotencyKey(key)
                .map(request -> new ExistingRequest(
                        request.getRequestHash(), request.getStatus(),
                        request.getSchedule() == null ? null : request.getSchedule().getId(),
                        request.getResponseJson()));
    }

    @Transactional
    public boolean reserve(String key, UUID previewId, String requestHash, OffsetDateTime now) {
        SchedulePreview preview = previewRepository.findForUpdateById(previewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_PREVIEW_NOT_FOUND));
        ScheduleCreationRequest existing = requestRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if ("FAILED".equals(existing.getStatus())) {
                existing.retry();
                return true;
            }
            return false;
        }
        UUID scheduleId = scheduleRepository.findIdByPreviewId(previewId).orElse(null);
        if (scheduleId != null || "CONSUMED".equals(preview.getStatus())) {
            throw new PreviewAlreadyConsumedException(scheduleId);
        }
        if (requestRepository.findFirstByPreview_IdAndStatusIn(
                previewId, List.of("IN_PROGRESS", "COMPLETED")).isPresent()) {
            throw new BusinessException(ErrorCode.SCHEDULE_CREATION_IN_PROGRESS);
        }
        requestRepository.save(new ScheduleCreationRequest(key, preview, requestHash, now));
        return true;
    }

    @Transactional
    public void complete(
            String key,
            UUID previewId,
            UUID scheduleId,
            String responseJson,
            OffsetDateTime now
    ) {
        ScheduleCreationRequest request = requestRepository.findByIdempotencyKey(key).orElseThrow();
        SchedulePreview preview = previewRepository.findById(previewId).orElseThrow();
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        schedule.applyPreview(
                preview, preview.getTimeZone(), preview.getLodgingMode(), preview.getRouteCoverage(),
                schedule.getPlanningWarningsJson());
        preview.consume(now);
        request.complete(schedule, 201, responseJson, now);
    }

    @Transactional
    public void fail(String key, String errorCode) {
        requestRepository.findByIdempotencyKey(key).ifPresent(request -> request.fail(errorCode));
    }

    public record ExistingRequest(
            String requestHash,
            String status,
            UUID scheduleId,
            String responseJson
    ) { }
}
