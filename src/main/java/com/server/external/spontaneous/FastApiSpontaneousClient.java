package com.server.external.spontaneous;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.schedule.dto.ScheduleResponse;
import com.server.spontaneous.dto.SpontaneousCourseRequest;
import com.server.spontaneous.dto.SpontaneousCourseResponse;
import com.server.spontaneous.dto.SpontaneousDestinationRequest;
import com.server.spontaneous.dto.SpontaneousDestinationResponse;
import com.server.spontaneous.dto.SpontaneousScheduleRequest;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FastApiSpontaneousClient {

    private static final Logger log = LoggerFactory.getLogger(FastApiSpontaneousClient.class);
    private static final String OWNER_HEADER = "X-Auth-User-Id";

    private final RestClient restClient;
    private final FastApiSpontaneousProperties properties;
    private final ObjectMapper objectMapper;

    public FastApiSpontaneousClient(
            @Qualifier("fastApiSpontaneousRestClient") RestClient restClient,
            FastApiSpontaneousProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public SpontaneousDestinationResponse recommendDestinations(
            SpontaneousDestinationRequest request
    ) {
        ensureEnabled();

        try {
            return executeWithLogging(
                    "recommendDestinations",
                    () -> restClient.post()
                            .uri("/api/v1/spontaneous-trips/destinations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(SpontaneousDestinationResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapSpontaneousError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI spontaneous destinations access failure: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public SpontaneousCourseResponse recommendCourse(
            SpontaneousCourseRequest request
    ) {
        return recommendCourse(request, null);
    }

    public SpontaneousCourseResponse recommendCourse(
            SpontaneousCourseRequest request,
            Long ownerId
    ) {
        ensureEnabled();

        try {
            return executeWithLogging(
                    "recommendCourse",
                    () -> restClient.post()
                            .uri("/api/v1/spontaneous-trips/course")
                            .contentType(MediaType.APPLICATION_JSON)
                            .headers(headers -> {
                                if (ownerId != null) {
                                    headers.set(OWNER_HEADER, ownerId.toString());
                                }
                            })
                            .body(request)
                            .retrieve()
                            .body(SpontaneousCourseResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapSpontaneousError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI spontaneous course access failure: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public ScheduleResponse saveSchedule(
            SpontaneousScheduleRequest request,
            String idempotencyKey,
            Long ownerId
    ) {
        ensureEnabled();
        try {
            return executeWithLogging(
                    "saveSchedule",
                    () -> restClient.post()
                            .uri("/api/v1/spontaneous-trips/schedules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", idempotencyKey)
                            .header(OWNER_HEADER, ownerId.toString())
                            .body(request)
                            .retrieve()
                            .body(ScheduleResponse.class)
            );
        } catch (RestClientResponseException exception) {
            throw mapSpontaneousError(exception);
        } catch (ResourceAccessException exception) {
            log.warn("FastAPI spontaneous schedule access failure: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
        }
    }

    private <T> T executeWithLogging(String operation, Supplier<T> call) {
        long startedAt = System.currentTimeMillis();
        try {
            T result = call.get();
            log.info("FastAPI spontaneous {} succeeded in {} ms", operation,
                    System.currentTimeMillis() - startedAt);
            return result;
        } catch (RuntimeException exception) {
            log.warn("FastAPI spontaneous {} failed in {} ms", operation,
                    System.currentTimeMillis() - startedAt);
            throw exception;
        }
    }

    private BusinessException mapSpontaneousError(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        String detail = extractDetail(body);
        log.warn("FastAPI spontaneous request failed. statusCode={}, detail={}, responseBody={}",
                exception.getStatusCode(), detail, body);

        return new BusinessException(errorCodeFor(exception.getStatusCode().value(), detail), exception);
    }

    private ErrorCode errorCodeFor(int statusCode, String detail) {
        return switch (detail) {
            case "INVALID_TIME_RANGE" -> ErrorCode.INVALID_SPONTANEOUS_TRIP_REQUEST;
            case "DESTINATION_NOT_FOUND" -> ErrorCode.SPONTANEOUS_DESTINATION_NOT_FOUND;
            case "DESTINATIONS_NOT_FOUND" -> ErrorCode.SPONTANEOUS_DESTINATIONS_NOT_FOUND;
            case "COURSE_NOT_FEASIBLE" -> ErrorCode.SPONTANEOUS_COURSE_NOT_FEASIBLE;
            case "NO_ROUTE" -> ErrorCode.SPONTANEOUS_ROUTE_NOT_FOUND;
            case "TOUR_API_ERROR", "EXTERNAL_ROUTING_API_ERROR" -> ErrorCode.SPONTANEOUS_PROVIDER_ERROR;
            case "TOUR_API_NOT_CONFIGURED", "ODSAY_AUTH_FAILED", "ODSAY_QUOTA_EXCEEDED",
                    "TMAP_QUOTA_EXCEEDED" ->
                    ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE;
            case "SPONTANEOUS_PREVIEW_INVALID" -> ErrorCode.SPONTANEOUS_PREVIEW_INVALID;
            case "SPONTANEOUS_PREVIEW_OWNER_MISMATCH" -> ErrorCode.SPONTANEOUS_PREVIEW_OWNER_MISMATCH;
            case "SPONTANEOUS_PREVIEW_EXPIRED" -> ErrorCode.SPONTANEOUS_PREVIEW_EXPIRED;
            case "SPONTANEOUS_PREVIEW_ALREADY_SAVED" -> ErrorCode.SPONTANEOUS_PREVIEW_ALREADY_SAVED;
            case "SPONTANEOUS_PLACE_HIDDEN" -> ErrorCode.SPONTANEOUS_PLACE_HIDDEN;
            case "SPONTANEOUS_RETURN_TIME_EXCEEDED" -> ErrorCode.SPONTANEOUS_RETURN_TIME_EXCEEDED;
            case "SCHEDULE_DATABASE_REQUIRED" -> ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE;
            case "IDEMPOTENCY_KEY_REQUIRED" -> ErrorCode.IDEMPOTENCY_KEY_REQUIRED;
            case "IDEMPOTENCY_KEY_REUSED" -> ErrorCode.IDEMPOTENCY_KEY_REUSED;
            case "SCHEDULE_CREATION_IN_PROGRESS" -> ErrorCode.SCHEDULE_CREATION_IN_PROGRESS;
            default -> fallbackErrorCodeFor(statusCode);
        };
    }

    private ErrorCode fallbackErrorCodeFor(int statusCode) {
        return switch (statusCode) {
            case 400, 422 -> ErrorCode.INVALID_SPONTANEOUS_TRIP_REQUEST;
            case 404 -> ErrorCode.SPONTANEOUS_DESTINATION_NOT_FOUND;
            case 502 -> ErrorCode.SPONTANEOUS_PROVIDER_ERROR;
            case 401, 429, 503 -> ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE;
            default -> ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE;
        };
    }

    private String extractDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode detail = root.get("detail");
            if (detail != null && detail.isTextual()) {
                return detail.asText();
            }
        } catch (Exception exception) {
            log.warn("Failed to parse FastAPI spontaneous error body: {}", exception.getMessage());
        }

        return "";
    }
}
