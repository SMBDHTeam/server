package com.server.place.ingestion;

public record TourApiPlaceIngestionResult(
        int fetched,
        int discovered,
        int enriched,
        int unchanged,
        int pending,
        int failed,
        int skipped,
        int apiRequests,
        boolean lockSkipped
) {
    public static TourApiPlaceIngestionResult locked() {
        return new TourApiPlaceIngestionResult(0, 0, 0, 0, 0, 0, 0, 0, true);
    }
}
