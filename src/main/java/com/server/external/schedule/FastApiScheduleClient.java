package com.server.external.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.FieldViolation;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleListResponse;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import com.server.schedule.dto.SchedulePreviewScheduleRequest;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FastApiScheduleClient {

    /**
     * 일정 소유자를 FastAPI 에 알린다.
     *
     * <p>Spring 과 FastAPI 사이의 내부 호출에만 쓴다. 외부에서 들어오는 헤더가 아니라
     * 검증된 토큰에서 꺼낸 값이므로 FastAPI 가 그대로 신뢰해도 된다. 두 컨테이너는
     * 같은 도커 네트워크에 있고 FastAPI 는 외부에 공개되지 않는다.
     */
    private static final String OWNER_HEADER = "X-Auth-User-Id";

    private static final Logger log = LoggerFactory.getLogger(FastApiScheduleClient.class);

    private final RestClient restClient;
    private final FastApiScheduleProperties properties;
    private final ObjectMapper objectMapper;

    public FastApiScheduleClient(
            @Qualifier("fastApiScheduleRestClient") RestClient restClient,
            FastApiScheduleProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public SchedulePreviewResponse createPreview(SchedulePreviewCreateRequest request) {
        try {
            SchedulePreviewCreateRequest normalized = normalizePreviewRequest(request);
            return executeWithLogging(
                    "createPreview",
                    "startDate=%s, endDate=%s".formatted(normalized.startDate(), normalized.endDate()),
                    () -> restClient.post()
                    .uri("/api/v1/schedule-previews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(writeJson(normalized))
                    .retrieve()
                    .body(SchedulePreviewResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapPreviewError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI createPreview access failure: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public SchedulePreviewResponse getPreview(UUID previewId) {
        try {
            return executeWithLogging(
                    "getPreview",
                    "previewId=%s".formatted(previewId),
                    () -> restClient.get()
                    .uri("/api/v1/schedule-previews/{previewId}", previewId)
                    .retrieve()
                    .body(SchedulePreviewResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapPreviewError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI getPreview access failure. previewId={}, reason={}",
                    previewId, exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse createSchedule(ScheduleCreateRequest request, Long ownerId) {
        try {
            return executeWithLogging(
                    "createSchedule",
                    "startDate=%s, endDate=%s, ownerId=%s"
                            .formatted(request.startDate(), request.endDate(), ownerId),
                    () -> withOwner(restClient.post()
                            .uri("/api/v1/schedules")
                            .contentType(MediaType.APPLICATION_JSON), ownerId)
                    .body(writeJson(request))
                    .retrieve()
                    .body(ScheduleResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI createSchedule access failure: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse createScheduleFromPreview(
            SchedulePreviewScheduleRequest request,
            String idempotencyKey,
            Long ownerId
    ) {
        try {
            return executeWithLogging(
                    "createScheduleFromPreview",
                    "previewId=%s, idempotencyKey=%s, ownerId=%s"
                            .formatted(request.previewId(), idempotencyKey, ownerId),
                    () -> withOwner(restClient.post()
                            .uri("/api/v1/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", idempotencyKey), ownerId)
                    .body(writeJson(request))
                    .retrieve()
                    .body(ScheduleResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI createScheduleFromPreview access failure. previewId={}, idempotencyKey={}, reason={}",
                    request.previewId(), idempotencyKey, exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleListResponse listSchedules() {
        try {
            return executeWithLogging(
                    "listSchedules",
                    "",
                    () -> restClient.get()
                    .uri("/api/v1/schedules")
                    .retrieve()
                    .body(ScheduleListResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI listSchedules access failure: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse getSchedule(UUID scheduleId) {
        try {
            return executeWithLogging(
                    "getSchedule",
                    "scheduleId=%s".formatted(scheduleId),
                    () -> restClient.get()
                    .uri("/api/v1/schedules/{scheduleId}", scheduleId)
                    .retrieve()
                    .body(ScheduleResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI getSchedule access failure. scheduleId={}, reason={}",
                    scheduleId, exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse updateSchedule(UUID scheduleId, ScheduleUpdateRequest request) {
        try {
            return executeWithLogging(
                    "updateSchedule",
                    "scheduleId=%s".formatted(scheduleId),
                    () -> restClient.patch()
                    .uri("/api/v1/schedules/{scheduleId}", scheduleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(writeJson(request))
                    .retrieve()
                    .body(ScheduleResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI updateSchedule access failure. scheduleId={}, reason={}",
                    scheduleId, exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleMapResponse getScheduleMap(UUID scheduleId, Integer dayNo) {
        try {
            return executeWithLogging(
                    "getScheduleMap",
                    "scheduleId=%s, dayNo=%s".formatted(scheduleId, dayNo),
                    () -> restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/schedules/{scheduleId}/map");
                        if (dayNo != null) {
                            builder = builder.queryParam("dayNo", dayNo);
                        }
                        return builder.build(scheduleId);
                    })
                    .retrieve()
                    .body(ScheduleMapResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI getScheduleMap access failure. scheduleId={}, dayNo={}, reason={}",
                    scheduleId, dayNo, exception.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private <T> T executeWithLogging(String operation, String context, Supplier<T> call) {
        long startedAt = System.currentTimeMillis();
        try {
            T result = call.get();
            log.info("FastAPI {} succeeded in {} ms{}",
                    operation,
                    System.currentTimeMillis() - startedAt,
                    context.isBlank() ? "" : " [" + context + "]");
            return result;
        } catch (RuntimeException exception) {
            log.warn("FastAPI {} failed in {} ms{}",
                    operation,
                    System.currentTimeMillis() - startedAt,
                    context.isBlank() ? "" : " [" + context + "]");
            throw exception;
        }
    }

    private BusinessException mapPreviewError(RestClientResponseException exception) {
        log.warn("FastAPI preview request failed. statusCode={}, responseBody={}",
                exception.getStatusCode(), exception.getResponseBodyAsString());
        return switch (exception.getStatusCode().value()) {
            case 400, 422 -> new BusinessException(
                    ErrorCode.INVALID_SCHEDULE_PREVIEW_REQUEST,
                    extractFieldViolations(exception.getResponseBodyAsString())
            );
            case 404 -> new BusinessException(ErrorCode.SCHEDULE_PREVIEW_NOT_FOUND, exception);
            case 410 -> new BusinessException(ErrorCode.PREVIEW_EXPIRED, exception);
            default -> new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        };
    }

    private BusinessException mapScheduleError(RestClientResponseException exception) {
        log.warn("FastAPI schedule request failed. statusCode={}, responseBody={}",
                exception.getStatusCode(), exception.getResponseBodyAsString());
        return switch (exception.getStatusCode().value()) {
            case 400, 422 -> new BusinessException(
                    ErrorCode.INVALID_SCHEDULE_CONDITION,
                    extractFieldViolations(exception.getResponseBodyAsString())
            );
            case 404 -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND, exception);
            case 409 -> mapConflictError(exception);
            case 410 -> new BusinessException(ErrorCode.PREVIEW_EXPIRED, exception);
            default -> new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        };
    }

    private BusinessException mapConflictError(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body != null && body.contains("in progress")) {
            return new BusinessException(ErrorCode.SCHEDULE_CREATION_IN_PROGRESS, exception);
        }
        if (body != null && body.contains("already")) {
            return new BusinessException(ErrorCode.PREVIEW_ALREADY_CONSUMED, exception);
        }
        return new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED, exception);
    }

    /** 로그인하지 않은 요청이면 헤더를 붙이지 않는다. FastAPI 는 소유자 없는 일정으로 저장한다. */
    private org.springframework.web.client.RestClient.RequestBodySpec withOwner(
            org.springframework.web.client.RestClient.RequestBodySpec spec, Long ownerId) {
        return ownerId == null ? spec : spec.header(OWNER_HEADER, String.valueOf(ownerId));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize FastAPI schedule request", exception);
        }
    }

    private SchedulePreviewCreateRequest normalizePreviewRequest(SchedulePreviewCreateRequest request) {
        return new SchedulePreviewCreateRequest(
                request.startDate(),
                request.endDate(),
                request.startLocation(),
                request.startTime(),
                normalizeLodgingPlan(request.lodgingPlan()),
                request.endConstraint(),
                request.selectedAnswers(),
                request.mustVisitPlaceIdsOrEmpty(),
                request.fixedEventsOrEmpty(),
                request.dayOverridesOrEmpty(),
                request.customPrompt(),
                request.timeZone()
        );
    }

    private SchedulePreviewCreateRequest.LodgingPlan normalizeLodgingPlan(
            SchedulePreviewCreateRequest.LodgingPlan lodgingPlan
    ) {
        return new SchedulePreviewCreateRequest.LodgingPlan(
                lodgingPlan.mode(),
                lodgingPlan.baseLocation(),
                lodgingPlan.nightStaysOrEmpty()
        );
    }

    private List<FieldViolation> extractFieldViolations(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode detail = root.path("detail");
            if (detail.isMissingNode() || detail.isNull()) {
                return List.of();
            }
            if (detail.isTextual()) {
                return List.of(FieldViolation.of("", detail.asText()));
            }
            if (!detail.isArray()) {
                return List.of();
            }

            List<FieldViolation> violations = new ArrayList<>();
            for (JsonNode item : detail) {
                String fieldPath = toFieldPath(item.path("loc"));
                String message = item.path("msg").asText("");
                if (message.isBlank()) {
                    continue;
                }
                violations.add(FieldViolation.of(fieldPath, message));
            }
            return violations;
        } catch (JsonProcessingException exception) {
            log.debug("Failed to parse FastAPI validation response body: {}", responseBody, exception);
            return List.of();
        }
    }

    private String toFieldPath(JsonNode locationNode) {
        if (!locationNode.isArray()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (JsonNode part : locationNode) {
            String value = part.asText();
            if ("body".equals(value)) {
                continue;
            }
            parts.add(value);
        }
        return String.join(".", parts);
    }
}
