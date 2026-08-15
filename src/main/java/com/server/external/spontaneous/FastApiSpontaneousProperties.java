package com.server.external.spontaneous;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.spontaneous-fastapi")
public record FastApiSpontaneousProperties(
        boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public FastApiSpontaneousProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://127.0.0.1:8010";
        }

        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }

        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
    }
}