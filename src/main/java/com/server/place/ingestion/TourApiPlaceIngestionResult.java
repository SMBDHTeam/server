package com.server.place.ingestion;

public record TourApiPlaceIngestionResult(
        int fetched,
        int saved,
        int skipped
) {

    public TourApiPlaceIngestionResult plus(TourApiPlaceIngestionResult other) {
        return new TourApiPlaceIngestionResult(
                fetched + other.fetched(),
                saved + other.saved(),
                skipped + other.skipped()
        );
    }

    public static TourApiPlaceIngestionResult empty() {
        return new TourApiPlaceIngestionResult(0, 0, 0);
    }
}
