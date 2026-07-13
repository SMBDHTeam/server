package com.server.place.ingestion;

import java.util.List;

record PlaceEnrichmentItem(
        String overview,
        String homepage,
        String detailRawJson,
        String openingHoursText,
        String closedDaysText,
        String useFeeText,
        String parkingText,
        boolean requiresManualCheck,
        String operatingRawJson,
        List<Image> images
) {
    PlaceEnrichmentItem {
        images = images == null ? List.of() : List.copyOf(images);
    }

    boolean hasDetail() {
        return detailRawJson != null;
    }

    boolean hasOperatingInfo() {
        return operatingRawJson != null;
    }

    record Image(String url, String thumbnailUrl, String copyrightType) {
    }
}
