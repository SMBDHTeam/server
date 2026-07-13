package com.server.place.ingestion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

record PlaceDiscoveryItem(
        String externalContentId,
        String contentTypeId,
        String name,
        String category,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        String primaryImageUrl,
        LocalDateTime sourceModifiedAt
) {
}
