package com.server.external.tmap;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.tmap")
public record TmapProperties(
        String baseUrl,
        String appKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    public TmapProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.openapi.sk.com";
        }
        if (appKey == null) {
            appKey = "";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
