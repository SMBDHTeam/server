package com.server.external.odsay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.odsay")
public record OdsayProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        Duration minRequestInterval
) {

    public OdsayProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.odsay.com";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
        if (minRequestInterval == null || minRequestInterval.isNegative()) {
            minRequestInterval = Duration.ofMillis(150);
        }
    }
}
