package com.server.external.tourapi;

import java.util.List;

public record TourApiPlaceImageResponse(
        List<Item> items
) {

    public TourApiPlaceImageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(
            String url,
            String thumbnailUrl,
            String copyrightType
    ) {
    }
}
