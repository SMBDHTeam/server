package com.server.place.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlaceSearchResponse(
        List<Item> items
) {

    public record Item(
            Long id,
            String externalContentId,
            String name,
            String category,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer distanceMeters,
            String primaryImageUrl
    ) {
    }
}
