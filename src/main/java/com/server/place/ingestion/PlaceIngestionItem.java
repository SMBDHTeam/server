package com.server.place.ingestion;

import java.math.BigDecimal;
import java.util.List;

record PlaceIngestionItem(
        String externalContentId,
        String contentTypeId,
        String name,
        String category,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String primaryImageUrl,
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

    PlaceIngestionItem {
        images = images == null ? List.of() : List.copyOf(images);
    }

    record Image(
            String url,
            String thumbnailUrl,
            String copyrightType
    ) {
    }
}
