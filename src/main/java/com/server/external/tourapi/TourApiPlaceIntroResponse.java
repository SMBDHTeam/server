package com.server.external.tourapi;

public record TourApiPlaceIntroResponse(
        String openingHoursText,
        String closedDaysText,
        String useFeeText,
        String parkingText,
        boolean requiresManualCheck,
        String rawJson
) {

    public static TourApiPlaceIntroResponse empty() {
        return new TourApiPlaceIntroResponse(null, null, null, null, false, null);
    }
}
