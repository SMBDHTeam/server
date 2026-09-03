package com.server.external.spontaneous;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FastApiSpontaneousClient {

    private final RestClient restClient;
    private final FastApiSpontaneousProperties properties;

    public FastApiSpontaneousClient(
            @Qualifier("fastApiSpontaneousRestClient") RestClient restClient,
            FastApiSpontaneousProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public Map<String, Object> recommendDestinations(
            Map<String, Object> request
    ) {
        if (!properties.enabled()) {
            throw new IllegalStateException(
                    "Spontaneous FastAPI is disabled"
            );
        }

        return restClient.post()
                .uri("/api/v1/spontaneous-trips/destinations")
                .body(request)
                .retrieve()
                .body(Map.class);
    }

    public Map<String, Object> recommendCourse(
            Map<String, Object> request
    ) {
        if (!properties.enabled()) {
            throw new IllegalStateException(
                    "Spontaneous FastAPI is disabled"
            );
        }

        return restClient.post()
                .uri("/api/v1/spontaneous-trips/course")
                .body(request)
                .retrieve()
                .body(Map.class);
    }
}