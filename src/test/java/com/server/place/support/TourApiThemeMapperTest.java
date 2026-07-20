package com.server.place.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.place.domain.PlaceDetail;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TourApiThemeMapperTest {

    @Test
    void derivesPrimaryThemeFromContentType() {
        Place restaurant = place("39", "수변최고돼지국밥", "음식점");

        assertThat(TourApiThemeMapper.primaryTheme(restaurant))
                .contains(TourApiTheme.FOOD);
        assertThat(TourApiThemeMapper.matchesTheme(restaurant, TourApiTheme.FOOD.answerId()))
                .isTrue();
    }

    @Test
    void augmentsThemeFromKeywords() {
        Place cableCar = place("12", "송도해상케이블카", "관광지");

        assertThat(TourApiThemeMapper.themes(cableCar))
                .contains(TourApiTheme.NATURE, TourApiTheme.ACTIVITY);
        assertThat(TourApiThemeMapper.matchesTheme(cableCar, TourApiTheme.ACTIVITY.answerId()))
                .isTrue();
    }

    @Test
    void matchesThemeTextForCulture() {
        assertThat(TourApiThemeMapper.matchesThemeText(
                TourApiTheme.CULTURE.answerId(), "부산근현대역사관"))
                .isTrue();
    }

    @Test
    void usesDetailOverviewForKeywordClassification() {
        Place place = place("12", "범어사", "관광지");
        new PlaceDetail(place, "부산의 대표적인 전통 사찰과 역사 문화 유적지", null, null);

        assertThat(TourApiThemeMapper.themes(place))
                .contains(TourApiTheme.NATURE, TourApiTheme.CULTURE);
    }

    private Place place(String contentTypeId, String name, String category) {
        return new Place(
                "TOUR_API",
                "TEST-" + name,
                contentTypeId,
                name,
                category,
                "부산",
                new BigDecimal("129.1186"),
                new BigDecimal("35.1532"),
                null
        );
    }
}
