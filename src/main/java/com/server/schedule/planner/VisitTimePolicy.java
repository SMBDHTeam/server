package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.time.LocalTime;

/**
 * Applies only soft quality penalties. Structured operating hours are not available for
 * every place, so opening-hour hard gates remain outside this policy.
 */
public final class VisitTimePolicy {

    private static final LocalTime EVENING = LocalTime.of(17, 0);
    private static final LocalTime NIGHT = LocalTime.of(20, 0);
    private static final LocalTime MARKET_OPENING = LocalTime.of(10, 0);

    private VisitTimePolicy() {
    }

    public static int penalty(Place place, LocalTime arrival) {
        if (MealTimePolicy.isMealPlace(place)) return 0;
        PlaceExperienceClassifier.ExperienceProfile profile = PlaceExperienceClassifier.classify(place);
        if (profile.type() == PlaceExperienceClassifier.ExperienceType.EVENT) return 0;
        if (profile.atmospheres().contains(PlaceExperienceClassifier.AtmosphereType.NIGHT_VIEW)
                || profile.contribution(PlaceExperienceClassifier.AvailableExperience.NIGHT_VIEW) > 0) {
            return arrival.isBefore(EVENING) ? 90 : 0;
        }
        if (profile.type() == PlaceExperienceClassifier.ExperienceType.MARKET_COMMERCE) {
            return arrival.isBefore(MARKET_OPENING) ? 25 : 0;
        }
        if (profile.type() == PlaceExperienceClassifier.ExperienceType.EXHIBITION_MUSEUM
                && !arrival.isBefore(EVENING)) {
            return 35;
        }
        boolean outdoor = profile.environments().contains(PlaceExperienceClassifier.EnvironmentType.COASTAL)
                || profile.environments().contains(PlaceExperienceClassifier.EnvironmentType.GREEN);
        if (outdoor && !arrival.isBefore(NIGHT)) return 50;
        if (outdoor && !arrival.isBefore(EVENING)) return 20;
        return 0;
    }
}
