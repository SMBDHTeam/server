package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlannerRouteEstimatorTest {

    private final PlannerRouteEstimator estimator = new PlannerRouteEstimator();

    @Test
    void estimatesNearbyPointsAsWalkingWithoutExternalRouteData() {
        TransitRouteResult result = estimator.estimate(
                point("A", "129.0403", "35.1151"),
                point("B", "129.0450", "35.1180")
        );

        assertThat(result.provider()).isEqualTo("PLANNER_ESTIMATE");
        assertThat(result.segments()).singleElement()
                .satisfies(segment -> {
                    assertThat(segment.mode()).isEqualTo("WALK");
                    assertThat(segment.distanceMeters()).isPositive();
                });
        assertThat(result.routeLines()).isEmpty();
    }

    @Test
    void estimatesDistantPointsAsTransit() {
        TransitRouteResult result = estimator.estimate(
                point("부산역", "129.0403", "35.1151"),
                point("해운대", "129.1604", "35.1587")
        );

        assertThat(result.segments()).singleElement()
                .satisfies(segment -> assertThat(segment.mode()).isEqualTo("TRANSIT_ESTIMATE"));
        assertThat(result.totalMinutes()).isGreaterThan(8);
    }

    private TransitPoint point(String name, String longitude, String latitude) {
        return new TransitPoint(name, new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
