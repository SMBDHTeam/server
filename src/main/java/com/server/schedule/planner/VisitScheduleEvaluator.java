package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.time.LocalTime;

/** Provides the schedule-level soft quality evaluation for a planned visit time. */
public final class VisitScheduleEvaluator {

    private VisitScheduleEvaluator() {
    }

    public static int timeSuitabilityPenalty(Place place, LocalTime arrival) {
        return VisitTimePolicy.penalty(place, arrival);
    }
}
