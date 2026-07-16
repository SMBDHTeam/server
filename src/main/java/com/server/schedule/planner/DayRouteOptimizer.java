package com.server.schedule.planner;

import com.server.common.error.BusinessException;
import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.springframework.stereotype.Component;

@Component
public class DayRouteOptimizer {

    private static final int LONG_TRANSIT_THRESHOLD_MINUTES = 60;
    private static final int LONG_TRANSIT_PENALTY_MULTIPLIER = 2;
    private static final int MEAL_SLOT_MISS_PENALTY = 10_000;
    private static final int DAY_OVERRUN_MINUTE_PENALTY = 1_000;
    private static final int MINIMUM_FEASIBLE_STAY_MINUTES = 30;
    private static final int CONSECUTIVE_SAME_EXPERIENCE_PENALTY = 12;
    private static final int CONSECUTIVE_SAME_SEMANTIC_GROUP_PENALTY = 4;
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

    public OptimizedDayRoute optimize(
            ScheduleDay day,
            List<Place> places,
            RouteResolver routeResolver,
            OptimizationPreference preference
    ) {
        return optimize(day, places, routeResolver, preference, ignored -> 60);
    }

    public OptimizedDayRoute optimize(
            ScheduleDay day,
            List<Place> places,
            RouteResolver routeResolver,
            OptimizationPreference preference,
            ToIntFunction<Place> stayMinutes
    ) {
        return ranked(day, places, routeResolver, preference, stayMinutes, 1).get(0);
    }

    public List<OptimizedDayRoute> ranked(
            ScheduleDay day,
            List<Place> places,
            RouteResolver routeResolver,
            OptimizationPreference preference,
            ToIntFunction<Place> stayMinutes,
            int limit
    ) {
        if (places.isEmpty()) {
            return List.of(new OptimizedDayRoute(List.of(), List.of(), null, 0, 0, RouteFlowMetrics.empty()));
        }
        return rankOrders(
                day, permutations(places), routeResolver, preference, stayMinutes, limit, false);
    }

    public List<OptimizedDayRoute> rankedWithMealPositionDiversity(
            ScheduleDay day,
            List<Place> places,
            RouteResolver routeResolver,
            OptimizationPreference preference,
            ToIntFunction<Place> stayMinutes,
            int limit
    ) {
        if (places.isEmpty()) {
            return List.of(new OptimizedDayRoute(List.of(), List.of(), null, 0, 0, RouteFlowMetrics.empty()));
        }
        return rankOrders(
                day, permutations(places), routeResolver, preference, stayMinutes, limit, true);
    }

    public OptimizedDayRoute bestOf(
            ScheduleDay day,
            List<List<Place>> orders,
            RouteResolver routeResolver,
            OptimizationPreference preference,
            ToIntFunction<Place> stayMinutes
    ) {
        return rankOrders(day, orders, routeResolver, preference, stayMinutes, 1, false).get(0);
    }

    private List<OptimizedDayRoute> rankOrders(
            ScheduleDay day,
            List<List<Place>> orders,
            RouteResolver routeResolver,
            OptimizationPreference preference,
            ToIntFunction<Place> stayMinutes,
            int limit,
            boolean preserveMealPositions
    ) {
        List<OptimizedDayRoute> candidates = new ArrayList<>();
        BusinessException lastFailure = null;
        for (List<Place> order : orders) {
            try {
                candidates.add(evaluate(
                        day, order, routeResolver, preference, stayMinutes));
            } catch (BusinessException exception) {
                lastFailure = exception;
            }
        }
        if (!candidates.isEmpty()) {
            List<OptimizedDayRoute> withDetourRatios = applyDetourRatios(day, candidates);
            List<OptimizedDayRoute> sorted = withDetourRatios.stream()
                    .sorted(Comparator.comparingInt(OptimizedDayRoute::optimizationCost)
                            .thenComparingInt(OptimizedDayRoute::totalMinutes)
                            .thenComparing(route -> orderKey(route.places())))
                    .toList();
            if (!preserveMealPositions || MealTimePolicy.activeSlots(day).size() != 1) {
                return sorted.stream().limit(Math.max(1, limit)).toList();
            }
            Map<String, OptimizedDayRoute> selected = new LinkedHashMap<>();
            OptimizedDayRoute best = sorted.get(0);
            selected.put(orderKey(best.places()), best);
            for (int position = 0; position < placesAtMost(sorted); position++) {
                int mealPosition = position;
                sorted.stream()
                        .filter(route -> MealTimePolicy.isMealPlace(route.places().get(mealPosition)))
                        .findFirst()
                        .ifPresent(route -> selected.put(orderKey(route.places()), route));
            }
            sorted.stream().limit(Math.max(1, limit))
                    .forEach(route -> selected.put(orderKey(route.places()), route));
            return List.copyOf(selected.values());
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("No route permutation was evaluated");
    }

    private int placesAtMost(List<OptimizedDayRoute> routes) {
        return routes.isEmpty() ? 0 : routes.get(0).places().size();
    }

    private OptimizedDayRoute evaluate(
            ScheduleDay day,
            List<Place> order,
            RouteResolver routeResolver,
            OptimizationPreference preference,
            ToIntFunction<Place> stayMinutes
    ) {
        TransitPoint previous = day.getStartLongitude() == null ? null : new TransitPoint(
                day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
        List<TransitRouteResult> inboundRoutes = new ArrayList<>();
        int totalMinutes = 0;
        int optimizationCost = 0;
        LocalTime cursor = day.getStartTime();
        List<MealTimePolicy.MealSlot> mealSlots = MealTimePolicy.activeSlots(day);
        Set<MealTimePolicy.MealSlot> assignedMealSlots = EnumSet.noneOf(MealTimePolicy.MealSlot.class);
        int waitingMinutes = 0;
        for (Place place : order) {
            TransitPoint destination = point(place);
            if (previous != null) {
                TransitRouteResult route = routeResolver.resolve(previous, destination);
                inboundRoutes.add(route);
                totalMinutes += route.totalMinutes();
                optimizationCost += routeCost(route, preference);
                cursor = cursor.plusMinutes(route.totalMinutes());
            }
            MealTimePolicy.Alignment alignment = MealTimePolicy.alignArrival(
                    cursor, place, mealSlots, assignedMealSlots);
            cursor = alignment.arrival();
            waitingMinutes += alignment.waitingMinutes();
            if (alignment.slot() != null) {
                assignedMealSlots.add(alignment.slot());
            }
            optimizationCost += VisitTimePolicy.penalty(place, cursor);
            cursor = cursor.plusMinutes(Math.min(
                    stayMinutes.applyAsInt(place), MINIMUM_FEASIBLE_STAY_MINUTES));
            previous = destination;
        }
        TransitRouteResult finalRoute = null;
        if (day.getEndLongitude() != null) {
            finalRoute = routeResolver.resolve(previous, new TransitPoint(
                    day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude()));
            totalMinutes += finalRoute.totalMinutes();
            optimizationCost += routeCost(finalRoute, preference);
            cursor = cursor.plusMinutes(finalRoute.totalMinutes());
        }
        int expectedMeals = Math.min(
                mealSlots.size(),
                (int) order.stream().filter(MealTimePolicy::isMealPlace).count());
        optimizationCost += Math.max(0, expectedMeals - assignedMealSlots.size())
                * MEAL_SLOT_MISS_PENALTY;
        if (cursor.isAfter(day.getEndTime())) {
            optimizationCost += Math.toIntExact(
                    Duration.between(day.getEndTime(), cursor).toMinutes()
                            * DAY_OVERRUN_MINUTE_PENALTY);
        }
        optimizationCost += waitingMinutes;
        optimizationCost += MealTimePolicy.orderPenalty(day, order);
        optimizationCost += consecutiveExperiencePenalty(order);
        RouteFlowMetrics routeFlow = routeFlow(day, order);
        optimizationCost += routeFlow.totalPenalty();
        return new OptimizedDayRoute(
                List.copyOf(order),
                List.copyOf(inboundRoutes),
                finalRoute,
                totalMinutes,
                optimizationCost,
                routeFlow
        );
    }

    public static RouteFlowMetrics routeFlow(ScheduleDay day, List<Place> order) {
        List<RoutePoint> points = routePoints(day, order);
        if (points.size() < 2) return RouteFlowMetrics.empty();
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
        return new RouteFlowMetrics(transitions, reentries, directionPenalty, 1.0, totalPenalty);
    }

    private List<OptimizedDayRoute> applyDetourRatios(
            ScheduleDay day,
            List<OptimizedDayRoute> candidates
    ) {
        double shortestDistance = candidates.stream()
                .mapToDouble(candidate -> coordinateRouteDistance(day, candidate.places()))
                .filter(distance -> distance > 0)
                .min().orElse(0);
        if (shortestDistance == 0) return List.copyOf(candidates);
        return candidates.stream().map(candidate -> {
            double detourRatio = coordinateRouteDistance(day, candidate.places()) / shortestDistance;
            RouteFlowMetrics flow = candidate.routeFlow();
            RouteFlowMetrics detourAwareFlow = flow.withDetourRatio(detourRatio);
            return new OptimizedDayRoute(
                    candidate.places(), candidate.inboundRoutes(), candidate.finalRoute(),
                    candidate.totalMinutes(), candidate.optimizationCost()
                            + detourAwareFlow.totalPenalty() - flow.totalPenalty(),
                    detourAwareFlow);
        }).toList();
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

    private static double coordinateRouteDistance(ScheduleDay day, List<Place> order) {
        List<RoutePoint> points = routePoints(day, order);
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            distance += distanceMeters(
                    points.get(index - 1).longitude(), points.get(index - 1).latitude(),
                    points.get(index).longitude(), points.get(index).latitude());
        }
        return distance;
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

    private int consecutiveExperiencePenalty(List<Place> order) {
        int penalty = 0;
        for (int index = 1; index < order.size(); index++) {
            PlaceExperienceClassifier.ExperienceProfile previous =
                    PlaceExperienceClassifier.classify(order.get(index - 1));
            PlaceExperienceClassifier.ExperienceProfile current =
                    PlaceExperienceClassifier.classify(order.get(index));
            if (!isDiversityScored(previous) || !isDiversityScored(current)) continue;
            if (previous.type() == current.type()) {
                penalty += CONSECUTIVE_SAME_EXPERIENCE_PENALTY;
            }
            if (previous.semanticGroup() == current.semanticGroup()) {
                penalty += CONSECUTIVE_SAME_SEMANTIC_GROUP_PENALTY;
            }
        }
        return penalty;
    }

    private boolean isDiversityScored(PlaceExperienceClassifier.ExperienceProfile profile) {
        return profile.type() != PlaceExperienceClassifier.ExperienceType.OTHER
                && profile.semanticGroup() != PlaceExperienceClassifier.SemanticGroup.OTHER
                && profile.semanticGroup() != PlaceExperienceClassifier.SemanticGroup.FOOD_REST;
    }

    private String orderKey(List<Place> places) {
        return places.stream()
                .map(place -> place.getId() == null ? place.getName() : place.getId().toString())
                .reduce((left, right) -> left + ">" + right)
                .orElse("");
    }

    private int routeCost(TransitRouteResult route, OptimizationPreference preference) {
        long walkMinutes = route.segments()
                .stream()
                .filter(segment -> "WALK".equals(segment.mode()))
                .mapToLong(TransitRouteResult.Segment::durationMinutes)
                .sum();
        long transitSegments = route.segments()
                .stream()
                .filter(segment -> !"WALK".equals(segment.mode()))
                .count();
        int transfers = (int) Math.max(0, transitSegments - 1);
        return route.totalMinutes()
                + (int) walkMinutes * preference.walkPenaltyMultiplier()
                + transfers * preference.transferPenaltyMinutes()
                + Math.max(0, route.totalMinutes() - LONG_TRANSIT_THRESHOLD_MINUTES)
                        * LONG_TRANSIT_PENALTY_MULTIPLIER;
    }

    private List<List<Place>> permutations(List<Place> places) {
        List<List<Place>> permutations = new ArrayList<>();
        collectPermutations(new ArrayList<>(), new ArrayList<>(places), permutations);
        return permutations;
    }

    private void collectPermutations(
            List<Place> prefix,
            List<Place> remaining,
            List<List<Place>> permutations
    ) {
        if (remaining.isEmpty()) {
            permutations.add(List.copyOf(prefix));
            return;
        }
        for (int index = 0; index < remaining.size(); index++) {
            Place place = remaining.remove(index);
            prefix.add(place);
            collectPermutations(prefix, remaining, permutations);
            prefix.remove(prefix.size() - 1);
            remaining.add(index, place);
        }
    }

    private TransitPoint point(Place place) {
        return new TransitPoint(place.getName(), place.getLongitude(), place.getLatitude());
    }

    @FunctionalInterface
    public interface RouteResolver {
        TransitRouteResult resolve(TransitPoint origin, TransitPoint destination);
    }

    public record OptimizedDayRoute(
            List<Place> places,
            List<TransitRouteResult> inboundRoutes,
            TransitRouteResult finalRoute,
            int totalMinutes,
            int optimizationCost,
            RouteFlowMetrics routeFlow
    ) {
    }

    public record RouteFlowMetrics(
            int regionTransitionCount,
            int regionReentryCount,
            int directionReversalPenalty,
            double detourRatio,
            int totalPenalty
    ) {
        private static RouteFlowMetrics empty() {
            return new RouteFlowMetrics(0, 0, 0, 1.0, 0);
        }

        private RouteFlowMetrics withDetourRatio(double detourRatio) {
            return new RouteFlowMetrics(
                    regionTransitionCount, regionReentryCount, directionReversalPenalty,
                    detourRatio, totalPenalty + detourPenalty(detourRatio));
        }
    }

    private record RoutePoint(
            BigDecimal longitude,
            BigDecimal latitude,
            PlacePreferenceScorer.Neighborhood region
    ) {
    }

    public record OptimizationPreference(
            int walkPenaltyMultiplier,
            int transferPenaltyMinutes
    ) {
    }
}
