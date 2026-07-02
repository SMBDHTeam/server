package com.server.location.dto;

import java.math.BigDecimal;
import java.util.List;

public record LocationSearchResponse(
        List<Item> items
) {

    public record Item(
            String name,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String externalId,
            String source
    ) {
    }
}
