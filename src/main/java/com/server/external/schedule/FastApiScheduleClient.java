package com.server.external.schedule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleListResponse;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import com.server.schedule.dto.SchedulePreviewScheduleRequest;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import java.util.UUID;
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
            return restClient.post()
                    .uri("/api/v1/schedule-previews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(writeJson(request))
                    .retrieve()
                    .body(SchedulePreviewResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapPreviewError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public SchedulePreviewResponse getPreview(UUID previewId) {
        try {
            return restClient.get()
                    .uri("/api/v1/schedule-previews/{previewId}", previewId)
                    .retrieve()
                    .body(SchedulePreviewResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapPreviewError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse createSchedule(ScheduleCreateRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/schedules")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(writeJson(request))
                    .retrieve()
                    .body(ScheduleResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse createScheduleFromPreview(
            SchedulePreviewScheduleRequest request,
            String idempotencyKey
    ) {
        try {
            return restClient.post()
                    .uri("/api/v1/schedules")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(writeJson(request))
                    .retrieve()
                    .body(ScheduleResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleListResponse listSchedules() {
        try {
            return restClient.get()
                    .uri("/api/v1/schedules")
                    .retrieve()
                    .body(ScheduleListResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse getSchedule(UUID scheduleId) {
        try {
            return restClient.get()
                    .uri("/api/v1/schedules/{scheduleId}", scheduleId)
                    .retrieve()
                    .body(ScheduleResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse updateSchedule(UUID scheduleId, ScheduleUpdateRequest request) {
        try {
            return restClient.patch()
                    .uri("/api/v1/schedules/{scheduleId}", scheduleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(writeJson(request))
                    .retrieve()
                    .body(ScheduleResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleMapResponse getScheduleMap(UUID scheduleId, Integer dayNo) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/api/v1/schedules/{scheduleId}/map");
                        if (dayNo != null) {
                            builder = builder.queryParam("dayNo", dayNo);
                        }
                        return builder.build(scheduleId);
                    })
                    .retrieve()
                    .body(ScheduleMapResponse.class);
        } catch (RestClientResponseException exception) {
            throw mapScheduleError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private BusinessException mapPreviewError(RestClientResponseException exception) {
        log.warn("FastAPI preview request failed. statusCode={}, responseBody={}",
                exception.getStatusCode(), exception.getResponseBodyAsString());
        return switch (exception.getStatusCode().value()) {
            case 400 -> new BusinessException(ErrorCode.INVALID_SCHEDULE_PREVIEW_REQUEST, exception);
            case 404 -> new BusinessException(ErrorCode.SCHEDULE_PREVIEW_NOT_FOUND, exception);
            case 410 -> new BusinessException(ErrorCode.PREVIEW_EXPIRED, exception);
            case 422 -> new BusinessException(ErrorCode.INVALID_SCHEDULE_PREVIEW_REQUEST, exception);
            default -> new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        };
    }

    private BusinessException mapScheduleError(RestClientResponseException exception) {
        log.warn("FastAPI schedule request failed. statusCode={}, responseBody={}",
                exception.getStatusCode(), exception.getResponseBodyAsString());
        return switch (exception.getStatusCode().value()) {
            case 400 -> new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION, exception);
            case 404 -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND, exception);
            case 409 -> mapConflictError(exception);
            case 410 -> new BusinessException(ErrorCode.PREVIEW_EXPIRED, exception);
            case 422 -> new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION, exception);
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize FastAPI schedule request", exception);
        }
    }
}
