package com.server.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record PlaceSearchResponse(
        List<Item> items
) {

    public record Item(
            Long placeId,
            String source,
            String externalId,
            String name,
            String category,
            String categoryLabel,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer distanceMeters,
            String primaryImageUrl,
            String placeUrl,
            boolean resolved
    ) {
        public Item(
                Long placeId,
                String source,
                String externalId,
                String name,
                String category,
                String address,
                BigDecimal longitude,
                BigDecimal latitude,
                Integer distanceMeters,
                String primaryImageUrl,
                String placeUrl,
                boolean resolved
        ) {
            this(placeId, source, externalId, name, category,
                    com.server.place.support.PlaceCategoryLabelResolver.resolve(category, null),
                    address, longitude, latitude, distanceMeters, primaryImageUrl, placeUrl, resolved);
        }

        public Item(
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
            this(id, "TOUR_API", externalContentId, name, category,
                    com.server.place.support.PlaceCategoryLabelResolver.resolve(category, null), address, longitude, latitude,
                    distanceMeters, primaryImageUrl, null, true);
        }

        @JsonProperty("id")
        public Long id() { return placeId; }

        @JsonProperty("externalContentId")
        public String externalContentId() { return externalId; }
    }
}
