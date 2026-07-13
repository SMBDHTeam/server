package com.server.external.busanbims;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.busan-bims")
public record BusanBimsProperties(
        boolean enabled,
        String baseUrl,
        String serviceKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    public BusanBimsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/6260000/BusanBIMS";
        }
        if (serviceKey == null) {
            serviceKey = "";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
