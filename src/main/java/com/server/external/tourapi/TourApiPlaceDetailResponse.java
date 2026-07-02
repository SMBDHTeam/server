package com.server.external.tourapi;

public record TourApiPlaceDetailResponse(
        String overview,
        String homepage,
        String rawJson
) {

    public static TourApiPlaceDetailResponse empty() {
        return new TourApiPlaceDetailResponse(null, null, null);
    }
}
