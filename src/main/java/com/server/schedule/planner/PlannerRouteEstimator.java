package com.server.schedule.planner;

import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Provides a deterministic, provider-free route estimate for combinatorial search.
 * Actual transit routes are fetched only after an order has been selected.
 */
@Component
public class PlannerRouteEstimator {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final int WALK_THRESHOLD_METERS = 1_200;
    private static final int WALK_METERS_PER_MINUTE = 70;
    private static final int TRANSIT_METERS_PER_MINUTE = 300;
    private static final int TRANSIT_ACCESS_MINUTES = 8;

    public TransitRouteResult estimate(TransitPoint origin, TransitPoint destination) {
        int distanceMeters = distanceMeters(origin, destination);
        boolean walking = distanceMeters <= WALK_THRESHOLD_METERS;
        int minutes = walking
                ? Math.max(5, divideRoundUp(distanceMeters, WALK_METERS_PER_MINUTE))
                : TRANSIT_ACCESS_MINUTES + divideRoundUp(distanceMeters, TRANSIT_METERS_PER_MINUTE);
        String mode = walking ? "WALK" : "TRANSIT_ESTIMATE";
        return new TransitRouteResult(
                minutes,
                null,
                "PLANNER_ESTIMATE",
                "UNAVAILABLE",
                false,
                List.of(),
                List.of(new TransitRouteResult.Segment(
                        mode,
                        null,
                        null,
                        origin.name(),
                        null,
                        destination.name(),
                        null,
                        minutes,
                        distanceMeters,
                        null,
                        0,
                        "UNAVAILABLE"
                )),
                List.of(),
                "{}"
        );
    }

    private int divideRoundUp(int value, int divisor) {
        return Math.max(1, (value + divisor - 1) / divisor);
    }

    private int distanceMeters(TransitPoint origin, TransitPoint destination) {
        double fromLongitude = Math.toRadians(origin.longitude().doubleValue());
        double fromLatitude = Math.toRadians(origin.latitude().doubleValue());
        double toLongitude = Math.toRadians(destination.longitude().doubleValue());
        double toLatitude = Math.toRadians(destination.latitude().doubleValue());
        double longitudeDelta = toLongitude - fromLongitude;
        double latitudeDelta = toLatitude - fromLatitude;
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(fromLatitude) * Math.cos(toLatitude)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return (int) Math.round(EARTH_RADIUS_METERS * 2 * Math.atan2(
                Math.sqrt(haversine), Math.sqrt(1 - haversine)));
    }
}
