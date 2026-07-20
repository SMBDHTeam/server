package com.server.external.aitheme;

import com.server.place.domain.Place;
import com.server.place.support.TourApiTheme;
import java.util.List;
import java.util.Optional;

public interface PlaceThemePredictionClient {

    Optional<TourApiTheme> predictPrimaryTheme(Place place);

    default Optional<PlaceThemeInsight> predictInsight(Place place) {
        return Optional.empty();
    }

    record PlaceThemeInsight(
            TourApiTheme primaryTheme,
            List<TourApiTheme> secondaryThemes,
            List<String> semanticTags,
            boolean mealPlace,
            boolean lowMobilityFriendly,
            String clusterKey,
            String reason
    ) {
        public boolean matches(TourApiTheme theme) {
            return primaryTheme == theme || secondaryThemes.contains(theme);
        }
    }
}
