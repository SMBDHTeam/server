package com.server.schedule.planner;

import com.server.common.error.BusinessException;
import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteResult;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
            optimizationCost += VisitScheduleEvaluator.timeSuitabilityPenalty(place, cursor);
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
        optimizationCost += ExperienceSequenceEvaluator.consecutivePenalty(order);
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
        return routeFlowMetrics(RouteFlowEvaluator.evaluate(day, order));
    }

    private List<OptimizedDayRoute> applyDetourRatios(
            ScheduleDay day,
            List<OptimizedDayRoute> candidates
    ) {
        double shortestDistance = candidates.stream()
                .mapToDouble(candidate -> RouteFlowEvaluator.coordinateRouteDistance(day, candidate.places()))
                .filter(distance -> distance > 0)
                .min().orElse(0);
        if (shortestDistance == 0) return List.copyOf(candidates);
        return candidates.stream().map(candidate -> {
            double detourRatio = RouteFlowEvaluator.coordinateRouteDistance(day, candidate.places()) / shortestDistance;
            RouteFlowMetrics flow = candidate.routeFlow();
            RouteFlowMetrics detourAwareFlow = routeFlowMetrics(RouteFlowEvaluator.withDetourRatio(
                    routeFlowEvaluation(flow), detourRatio));
            return new OptimizedDayRoute(
                    candidate.places(), candidate.inboundRoutes(), candidate.finalRoute(),
                    candidate.totalMinutes(), candidate.optimizationCost()
                            + detourAwareFlow.totalPenalty() - flow.totalPenalty(),
                    detourAwareFlow);
        }).toList();
    }

    private static RouteFlowMetrics routeFlowMetrics(RouteFlowEvaluator.Evaluation evaluation) {
        return new RouteFlowMetrics(
                evaluation.regionTransitionCount(),
                evaluation.regionReentryCount(),
                evaluation.directionReversalPenalty(),
                evaluation.detourRatio(),
                evaluation.totalPenalty());
    }

    private static RouteFlowEvaluator.Evaluation routeFlowEvaluation(RouteFlowMetrics metrics) {
        return new RouteFlowEvaluator.Evaluation(
                metrics.regionTransitionCount(),
                metrics.regionReentryCount(),
                metrics.directionReversalPenalty(),
                metrics.detourRatio(),
                metrics.totalPenalty());
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

    }

    public record OptimizationPreference(
            int walkPenaltyMultiplier,
            int transferPenaltyMinutes
    ) {
    }
}
