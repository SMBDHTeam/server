package com.server.place.dto;

import java.math.BigDecimal;

public record PlaceResolveResponse(
        Long placeId,
        String source,
        String externalId,
        String name,
        String category,
        String categoryLabel,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String primaryImageUrl,
        String placeUrl,
        boolean resolved,
        boolean operatingInfoAvailable
) {
}
