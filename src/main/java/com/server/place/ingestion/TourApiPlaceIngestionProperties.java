package com.server.place.ingestion;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.place-ingestion.tour-api")
public record TourApiPlaceIngestionProperties(
        boolean enabled,
        String areaCode,
        List<String> contentTypeIds,
        int pageSize,
        int maxPages,
        int maxRequestsPerDay
) {

    public TourApiPlaceIngestionProperties {
        if (areaCode == null || areaCode.isBlank()) {
            areaCode = "6";
        }
        if (contentTypeIds == null || contentTypeIds.isEmpty()) {
            contentTypeIds = List.of("12", "14", "15", "28", "32", "38", "39");
        } else {
            contentTypeIds = List.copyOf(contentTypeIds);
        }
        if (pageSize <= 0) {
            pageSize = 100;
        }
        if (maxPages <= 0) {
            maxPages = 1;
        }
        if (maxRequestsPerDay <= 0 || maxRequestsPerDay > 1000) {
            maxRequestsPerDay = 900;
        }
    }
}
