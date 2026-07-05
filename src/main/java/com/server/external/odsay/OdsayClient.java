package com.server.external.odsay;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OdsayClient {

    private static final Logger log = LoggerFactory.getLogger(OdsayClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final OdsayProperties properties;

    public OdsayClient(RestClient odsayRestClient, OdsayProperties properties) {
        this.restClient = odsayRestClient;
        this.properties = properties;
    }

    public Map<String, Object> searchPublicTransitPath(
            BigDecimal startLongitude,
            BigDecimal startLatitude,
            BigDecimal endLongitude,
            BigDecimal endLatitude
    ) {
        if (properties.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        try {
            String uri = UriComponentsBuilder.fromPath("/v1/api/searchPubTransPathT")
                    .queryParam("SX", startLongitude)
                    .queryParam("SY", startLatitude)
                    .queryParam("EX", endLongitude)
                    .queryParam("EY", endLatitude)
                    .queryParam("apiKey", URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8))
                    .build(true)
                    .toUriString();
            Map<String, Object> response = restClient.get()
                    .uri(URI.create(baseUrl() + uri))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (response == null || response.isEmpty()) {
                throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
            }
            if (response.containsKey("error")) {
                log.warn("ODsay API returned an error response. code={}, message={}",
                        errorCode(response),
                        errorMessage(response));
                throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn("ODsay API request failed. statusCode={}", exception.getStatusCode());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        } catch (ResourceAccessException | IllegalArgumentException exception) {
            log.warn("ODsay API request failed. exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    public Optional<Map<String, Object>> loadLane(String mapObject) {
        if (properties.apiKey().isBlank() || mapObject == null || mapObject.isBlank()) {
            return Optional.empty();
        }
        try {
            String uri = UriComponentsBuilder.fromPath("/v1/api/loadLane")
                    .queryParam("mapObject", URLEncoder.encode(mapObject, StandardCharsets.UTF_8))
                    .queryParam("apiKey", URLEncoder.encode(properties.apiKey(), StandardCharsets.UTF_8))
                    .build(true)
                    .toUriString();
            Map<String, Object> response = restClient.get()
                    .uri(URI.create(baseUrl() + uri))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (response == null || response.isEmpty()) {
                return Optional.empty();
            }
            if (response.containsKey("error")) {
                log.warn("ODsay loadLane returned an error response. code={}, message={}",
                        errorCode(response),
                        errorMessage(response));
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (RestClientResponseException exception) {
            log.warn("ODsay loadLane request failed. statusCode={}", exception.getStatusCode());
            return Optional.empty();
        } catch (ResourceAccessException | IllegalArgumentException exception) {
            log.warn("ODsay loadLane request failed. exceptionType={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String baseUrl() {
        String baseUrl = properties.baseUrl();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String errorCode(Map<String, Object> response) {
        return errorText(response, "code");
    }

    private String errorMessage(Map<String, Object> response) {
        return errorText(response, "message");
    }

    @SuppressWarnings("unchecked")
    private String errorText(Map<String, Object> response, String key) {
        Object error = response.get("error");
        if (error instanceof List<?> errors && !errors.isEmpty()) {
            error = errors.get(0);
        }
        if (error instanceof Map<?, ?> errorMap) {
            Object value = ((Map<String, Object>) errorMap).get(key);
            return value == null ? "" : value.toString();
        }
        return "";
    }
}
