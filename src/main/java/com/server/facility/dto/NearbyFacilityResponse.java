package com.server.facility.dto;

import java.math.BigDecimal;
import java.util.List;

public record NearbyFacilityResponse(
        List<Item> items
) {

    public record Item(
            String externalId,
            String type,
            String name,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer distanceMeters,
            String placeUrl,
            String source
    ) {
    }
}
