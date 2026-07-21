package com.server.schedule.planner;

import com.server.place.domain.Place;

/** Shared deterministic dwell-time estimate for Planner evaluation and saved stops. */
public final class VisitDurationPolicy {

    private static final int DEFAULT_STAY_MINUTES = 60;
    private static final int EVENT_STAY_MINUTES = 90;
    private static final int MEAL_STAY_MINUTES = 75;

    private VisitDurationPolicy() {
    }

    public static int minutes(Place place) {
        if ("15".equals(place.getContentTypeId())) {
            return EVENT_STAY_MINUTES;
        }
        if ("39".equals(place.getContentTypeId())) {
            return MEAL_STAY_MINUTES;
        }
        return DEFAULT_STAY_MINUTES;
    }
}
