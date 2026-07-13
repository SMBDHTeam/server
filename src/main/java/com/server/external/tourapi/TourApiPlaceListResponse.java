package com.server.external.tourapi;

import java.util.List;

public record TourApiPlaceListResponse(
        int totalCount,
        List<Item> items
) {

    public TourApiPlaceListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(
            String contentId,
            String contentTypeId,
            String title,
            String category,
            String address,
            String longitude,
            String latitude,
            String firstImage,
            String modifiedTime,
            String rawJson
    ) {
    }
}
