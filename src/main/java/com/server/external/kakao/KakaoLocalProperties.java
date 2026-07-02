package com.server.external.kakao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.kakao.local")
public record KakaoLocalProperties(
        String baseUrl,
        String restApiKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    public KakaoLocalProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dapi.kakao.com";
        }
        if (restApiKey == null) {
            restApiKey = "";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
