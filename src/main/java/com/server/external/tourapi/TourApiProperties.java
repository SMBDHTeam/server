package com.server.external.tourapi;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.tour-api")
public record TourApiProperties(
        String baseUrl,
        String serviceKey,
        String mobileOs,
        String mobileApp,
        Duration connectTimeout,
        Duration readTimeout
) {

    public TourApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.data.go.kr/B551011/KorService2";
        }
        if (serviceKey == null) {
            serviceKey = "";
        }
        if (mobileOs == null || mobileOs.isBlank()) {
            mobileOs = "ETC";
        }
        if (mobileApp == null || mobileApp.isBlank()) {
            mobileApp = "tour-server";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
