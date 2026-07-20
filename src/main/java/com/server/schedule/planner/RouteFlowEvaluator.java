package com.server.schedule.planner;

import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Calculates route-flow quality independently from route-provider travel time. */
public final class RouteFlowEvaluator {

    private static final int REGION_TRANSITION_PENALTY = 4;
    private static final int REGION_REENTRY_PENALTY = 30;
    private static final int DIRECTION_REVERSAL_90_DEGREES_PENALTY = 3;
    private static final int DIRECTION_REVERSAL_120_DEGREES_PENALTY = 9;
    private static final int DIRECTION_REVERSAL_150_DEGREES_PENALTY = 18;
    private static final double DETOUR_RATIO_TOLERANCE = 1.15;
    private static final int DETOUR_PENALTY_PER_RATIO = 80;
    private static final int MIN_DIRECTION_ANALYSIS_LEG_METERS = 1_000;
    private static final int BASE_RETURN_RADIUS_METERS = 1_000;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private RouteFlowEvaluator() {
    }

    public static Evaluation evaluate(ScheduleDay day, List<Place> order) {
        List<RoutePoint> points = routePoints(day, order);
        if (points.size() < 2) return Evaluation.empty();
        int transitions = 0;
        int reentries = 0;
        Set<PlacePreferenceScorer.Neighborhood> departedRegions = new HashSet<>();
        PlacePreferenceScorer.Neighborhood currentRegion = points.get(0).region();
        for (int index = 1; index < points.size(); index++) {
            RoutePoint next = points.get(index);
            if (currentRegion != next.region()) {
                transitions++;
                departedRegions.add(currentRegion);
                boolean terminalBaseReturn = index == points.size() - 1
                        && sameBase(points.get(0), next);
                if (departedRegions.contains(next.region()) && !terminalBaseReturn) {
                    reentries++;
                }
                currentRegion = next.region();
            }
        }
        int directionPenalty = directionReversalPenalty(points);
        int totalPenalty = transitions * REGION_TRANSITION_PENALTY
                + reentries * REGION_REENTRY_PENALTY + directionPenalty;
        return new Evaluation(transitions, reentries, directionPenalty, 1.0, totalPenalty);
    }

    public static Evaluation withDetourRatio(Evaluation evaluation, double detourRatio) {
        return new Evaluation(
                evaluation.regionTransitionCount(),
                evaluation.regionReentryCount(),
                evaluation.directionReversalPenalty(),
                detourRatio,
                evaluation.totalPenalty() + detourPenalty(detourRatio)
        );
    }

    public static double coordinateRouteDistance(ScheduleDay day, List<Place> order) {
        List<RoutePoint> points = routePoints(day, order);
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            distance += distanceMeters(
                    points.get(index - 1).longitude(), points.get(index - 1).latitude(),
                    points.get(index).longitude(), points.get(index).latitude());
        }
        return distance;
    }

    private static int detourPenalty(double detourRatio) {
        if (detourRatio <= DETOUR_RATIO_TOLERANCE) return 0;
        return (int) Math.ceil((detourRatio - DETOUR_RATIO_TOLERANCE)
                * DETOUR_PENALTY_PER_RATIO);
    }

    private static List<RoutePoint> routePoints(ScheduleDay day, List<Place> order) {
        List<RoutePoint> points = new ArrayList<>();
        if (day.getStartLongitude() != null && day.getStartLatitude() != null) {
            points.add(point(day.getStartLongitude(), day.getStartLatitude()));
        }
        for (Place place : order) {
            points.add(point(place.getLongitude(), place.getLatitude()));
        }
        if (day.getEndLongitude() != null && day.getEndLatitude() != null) {
            points.add(point(day.getEndLongitude(), day.getEndLatitude()));
        }
        return List.copyOf(points);
    }

    private static RoutePoint point(BigDecimal longitude, BigDecimal latitude) {
        return new RoutePoint(
                longitude, latitude, PlacePreferenceScorer.Neighborhood.from(longitude, latitude));
    }

    private static boolean sameBase(RoutePoint start, RoutePoint end) {
        return distanceMeters(start.longitude(), start.latitude(), end.longitude(), end.latitude())
                <= BASE_RETURN_RADIUS_METERS;
    }

    private static int directionReversalPenalty(List<RoutePoint> points) {
        int penalty = 0;
        for (int index = 1; index < points.size() - 1; index++) {
            RoutePoint previous = points.get(index - 1);
            RoutePoint current = points.get(index);
            RoutePoint next = points.get(index + 1);
            if (distanceMeters(previous.longitude(), previous.latitude(), current.longitude(), current.latitude())
                    < MIN_DIRECTION_ANALYSIS_LEG_METERS
                    || distanceMeters(current.longitude(), current.latitude(), next.longitude(), next.latitude())
                    < MIN_DIRECTION_ANALYSIS_LEG_METERS) {
                continue;
            }
            double angle = turnAngleDegrees(previous, current, next);
            if (angle >= 150) {
                penalty += DIRECTION_REVERSAL_150_DEGREES_PENALTY;
            } else if (angle >= 120) {
                penalty += DIRECTION_REVERSAL_120_DEGREES_PENALTY;
            } else if (angle >= 90) {
                penalty += DIRECTION_REVERSAL_90_DEGREES_PENALTY;
            }
        }
        return penalty;
    }

    private static double turnAngleDegrees(RoutePoint previous, RoutePoint current, RoutePoint next) {
        double previousX = current.longitude().doubleValue() - previous.longitude().doubleValue();
        double previousY = current.latitude().doubleValue() - previous.latitude().doubleValue();
        double nextX = next.longitude().doubleValue() - current.longitude().doubleValue();
        double nextY = next.latitude().doubleValue() - current.latitude().doubleValue();
        double denominator = Math.hypot(previousX, previousY) * Math.hypot(nextX, nextY);
        if (denominator == 0) return 0;
        double cosine = Math.max(-1, Math.min(1, (previousX * nextX + previousY * nextY) / denominator));
        return Math.toDegrees(Math.acos(cosine));
    }

    private static int distanceMeters(
            BigDecimal fromLongitude,
            BigDecimal fromLatitude,
            BigDecimal toLongitude,
            BigDecimal toLatitude
    ) {
        double fromLongitudeRadians = Math.toRadians(fromLongitude.doubleValue());
        double fromLatitudeRadians = Math.toRadians(fromLatitude.doubleValue());
        double toLongitudeRadians = Math.toRadians(toLongitude.doubleValue());
        double toLatitudeRadians = Math.toRadians(toLatitude.doubleValue());
        double deltaLongitude = toLongitudeRadians - fromLongitudeRadians;
        double deltaLatitude = toLatitudeRadians - fromLatitudeRadians;
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(fromLatitudeRadians) * Math.cos(toLatitudeRadians)
                * Math.pow(Math.sin(deltaLongitude / 2), 2);
        return (int) Math.round(EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    public record Evaluation(
            int regionTransitionCount,
            int regionReentryCount,
            int directionReversalPenalty,
            double detourRatio,
            int totalPenalty
    ) {
        private static Evaluation empty() {
            return new Evaluation(0, 0, 0, 1.0, 0);
        }
    }

    private record RoutePoint(
            BigDecimal longitude,
            BigDecimal latitude,
            PlacePreferenceScorer.Neighborhood region
    ) {
    }
}
