package com.server.place.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlaceDetailResponse(
        Long id,
        String externalContentId,
        String contentTypeId,
        String name,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String overview,
        OperatingInfo operatingInfo,
        List<Image> images
) {

    public record OperatingInfo(
            String openingHoursText,
            String closedDaysText,
            String useFeeText,
            String parkingText,
            boolean requiresManualCheck
    ) {
    }

    public record Image(
            String url,
            String thumbnailUrl,
            String copyrightType
    ) {
    }
}
