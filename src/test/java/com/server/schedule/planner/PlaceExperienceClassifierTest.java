package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlaceExperienceClassifierTest {

    @Test
    void separatesBroadTourApiContentTypeIntoConcreteExperiences() {
        PlaceExperienceClassifier.ExperienceProfile beach =
                PlaceExperienceClassifier.classify(place("광안리해수욕장", "12"));
        assertThat(beach.type()).isEqualTo(PlaceExperienceClassifier.ExperienceType.BEACH_WALK);
        assertThat(beach.semanticGroup())
                .isEqualTo(PlaceExperienceClassifier.SemanticGroup.COASTAL_NATURE);
        assertThat(beach.environments())
                .contains(PlaceExperienceClassifier.EnvironmentType.COASTAL);
        assertThat(beach.contribution(PlaceExperienceClassifier.AvailableExperience.SEA_VIEW)).isEqualTo(100);
        assertThat(beach.contribution(PlaceExperienceClassifier.AvailableExperience.BEACH_WALK)).isEqualTo(100);

        assertThat(PlaceExperienceClassifier.classify(place("감천문화마을", "12")).type())
                .isEqualTo(PlaceExperienceClassifier.ExperienceType.VILLAGE_STREET_WALK);
        assertThat(PlaceExperienceClassifier.classify(place("부산박물관", "14")).type())
                .isEqualTo(PlaceExperienceClassifier.ExperienceType.EXHIBITION_MUSEUM);
        assertThat(PlaceExperienceClassifier.classify(place("부산시민공원", "12")).type())
                .isEqualTo(PlaceExperienceClassifier.ExperienceType.PARK_GREEN);
    }

    @Test
    void keepsVenueMeaningWhenAnEventOccursInACoastalPlace() {
        PlaceExperienceClassifier.ExperienceProfile festival =
                PlaceExperienceClassifier.classify(place("광안리 해변 불꽃축제", "15"));
        PlaceExperienceClassifier.ExperienceProfile beach =
                PlaceExperienceClassifier.classify(place("광안리해수욕장", "12"));

        assertThat(festival.type()).isEqualTo(PlaceExperienceClassifier.ExperienceType.EVENT);
        assertThat(festival.environments())
                .contains(PlaceExperienceClassifier.EnvironmentType.COASTAL);
        assertThat(festival.contribution(PlaceExperienceClassifier.AvailableExperience.EVENT_ATTENDANCE))
                .isEqualTo(100);
        assertThat(festival.contribution(PlaceExperienceClassifier.AvailableExperience.SEA_VIEW))
                .isEqualTo(60);
        assertThat(festival.contribution(PlaceExperienceClassifier.AvailableExperience.BEACH_WALK))
                .isZero();
        assertThat(PlaceExperienceClassifier.similarityPercent(festival, beach)).isGreaterThanOrEqualTo(65);
    }

    @Test
    void distinguishesRestaurantsFromCafeRestStops() {
        assertThat(PlaceExperienceClassifier.classify(place("해운대 돼지국밥", "39")).type())
                .isEqualTo(PlaceExperienceClassifier.ExperienceType.FOOD);
        assertThat(PlaceExperienceClassifier.classify(place("광안리 오션뷰 카페", "39")).type())
                .isEqualTo(PlaceExperienceClassifier.ExperienceType.CAFE_REST);
    }

    private Place place(String name, String contentTypeId) {
        return new Place(
                "TOUR_API", name, contentTypeId, name, "관광지", "부산",
                new BigDecimal("129.1000"), new BigDecimal("35.1500"), null
        );
    }
}
