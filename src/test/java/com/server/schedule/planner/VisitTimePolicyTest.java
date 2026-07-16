package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class VisitTimePolicyTest {

    @Test
    void prefersNightViewAfterEvening() {
        Place nightView = place("황령산 야경 전망대", "12");

        assertThat(VisitTimePolicy.penalty(nightView, LocalTime.of(14, 0))).isPositive();
        assertThat(VisitTimePolicy.penalty(nightView, LocalTime.of(18, 0))).isZero();
    }

    @Test
    void discouragesOutdoorVisitsAfterDarkWithoutRejectingThem() {
        Place beach = place("광안리해수욕장", "12");

        assertThat(VisitTimePolicy.penalty(beach, LocalTime.of(20, 0))).isEqualTo(50);
    }

    private Place place(String name, String contentTypeId) {
        return new Place(
                "TOUR_API", name, contentTypeId, name, "관광지", "부산",
                new BigDecimal("129.1"), new BigDecimal("35.1"), null);
    }
}
