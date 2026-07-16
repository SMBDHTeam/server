package com.server.schedule.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.schedule.domain.SchedulePreview;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import com.server.schedule.dto.SchedulePreviewScheduleRequest;
import com.server.schedule.dto.ScheduleResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ScheduleV2Service {

    private final SchedulePreviewService previewService;
    private final ScheduleService scheduleService;
    private final ScheduleCreationPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public ScheduleV2Service(
            SchedulePreviewService previewService,
            ScheduleService scheduleService,
            ScheduleCreationPersistenceService persistenceService,
            ObjectMapper objectMapper
    ) {
        this.previewService = previewService;
        this.scheduleService = scheduleService;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    public ScheduleResponse create(
            SchedulePreviewScheduleRequest request,
            String idempotencyKey
    ) {
        validateKey(idempotencyKey);
        String requestHash = sha256(request.previewId().toString());
        ScheduleCreationPersistenceService.ExistingRequest existing = persistenceService.find(idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!requestHash.equals(existing.requestHash())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if ("COMPLETED".equals(existing.status()) && existing.scheduleId() != null) {
                return readResponse(existing);
            }
        }

        SchedulePreview preview = previewService.requireGeneratable(request.previewId());
        boolean owner = persistenceService.reserve(
                idempotencyKey, request.previewId(), requestHash, OffsetDateTime.now());
        if (!owner) return waitForCompletedRequest(idempotencyKey, requestHash);
        try {
            SchedulePreviewCreateRequest input = previewService.readInput(preview);
            List<SchedulePreviewResponse.ResolvedDay> days = previewService.readResolvedDays(preview);
            List<String> warningCodes = previewService.readWarnings(preview).stream()
                    .map(SchedulePreviewResponse.Warning::code).toList();
            SchedulePreviewResponse.InterpretedPrompt interpretedPrompt =
                    previewService.readInterpretedPrompt(preview);
            ScheduleCreateRequest plannerRequest = toPlannerRequest(
                    input, days, interpretedPrompt.preferences());
            ScheduleResponse response = scheduleService.createFromPreview(
                    plannerRequest, preview, days, warningCodes,
                    input.fixedEventsOrEmpty(), input.customPrompt());
            persistenceService.complete(
                    idempotencyKey, request.previewId(), response.id(),
                    writeResponse(response), OffsetDateTime.now());
            return response;
        } catch (RuntimeException exception) {
            persistenceService.fail(idempotencyKey,
                    exception instanceof BusinessException business
                            ? business.getErrorCode().name() : "INTERNAL_ERROR");
            throw exception;
        }
    }

    private ScheduleResponse waitForCompletedRequest(String key, String requestHash) {
        for (int attempt = 0; attempt < 100; attempt++) {
            ScheduleCreationPersistenceService.ExistingRequest existing = persistenceService.find(key)
                    .orElse(null);
            if (existing != null && !requestHash.equals(existing.requestHash())) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            if (existing != null && "COMPLETED".equals(existing.status()) && existing.scheduleId() != null) {
                return readResponse(existing);
            }
            if (existing != null && "FAILED".equals(existing.status())) {
                throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new BusinessException(ErrorCode.SCHEDULE_CREATION_IN_PROGRESS);
    }

    private String writeResponse(ScheduleResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize idempotent schedule response", exception);
        }
    }

    private ScheduleResponse readResponse(ScheduleCreationPersistenceService.ExistingRequest existing) {
        if (existing.responseJson() == null) return scheduleService.get(existing.scheduleId());
        try {
            return objectMapper.readValue(existing.responseJson(), ScheduleResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize idempotent schedule response", exception);
        }
    }

    private ScheduleCreateRequest toPlannerRequest(
            SchedulePreviewCreateRequest input,
            List<SchedulePreviewResponse.ResolvedDay> days,
            List<String> promptPreferences
    ) {
        List<ScheduleCreateRequest.SelectedAnswer> answers = new ArrayList<>(input.selectedAnswers().stream()
                .flatMap(answer -> answer.answerIds().stream()
                        .map(answerId -> new ScheduleCreateRequest.SelectedAnswer(answer.questionId(), answerId)))
                .toList());
        if (promptPreferences.contains("LOW_WALKING")) {
            answers.add(new ScheduleCreateRequest.SelectedAnswer("PROMPT", "PROMPT_LOW_WALKING"));
        }
        if (promptPreferences.contains("PREFER_SEA_VIEW")) {
            answers.add(new ScheduleCreateRequest.SelectedAnswer("PROMPT", "PROMPT_PREFER_SEA_VIEW"));
        }
        if (promptPreferences.contains("PREFER_FOOD")) {
            answers.add(new ScheduleCreateRequest.SelectedAnswer("PROMPT", "PROMPT_PREFER_FOOD"));
        }
        Set<Long> mustVisitIds = new LinkedHashSet<>(input.mustVisitPlaceIdsOrEmpty());
        input.fixedEventsOrEmpty().forEach(event -> mustVisitIds.add(event.placeId()));
        ScheduleCreateRequest.Location start = toLocation(input.startLocation());
        SchedulePreviewResponse.Location finalLocation = days.get(days.size() - 1).endLocation();
        ScheduleCreateRequest.Location end = finalLocation == null
                ? start : new ScheduleCreateRequest.Location(
                        finalLocation.name(), finalLocation.longitude(), finalLocation.latitude());
        return new ScheduleCreateRequest(
                input.startDate(), input.endDate(), days.get(0).availableFrom(),
                days.get(days.size() - 1).availableUntil(), start, end, List.copyOf(answers),
                List.copyOf(mustVisitIds), List.of());
    }

    private ScheduleCreateRequest.Location toLocation(SchedulePreviewCreateRequest.Location location) {
        return new ScheduleCreateRequest.Location(location.name(), location.longitude(), location.latitude());
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
