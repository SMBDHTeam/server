package com.server.schedule.planner;

import com.server.common.error.BusinessException;
import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DayRouteOptimizer {

    public OptimizedDayRoute optimize(
            ScheduleDay day,
            List<Place> places,
            RouteResolver routeResolver,
            OptimizationPreference preference
    ) {
        if (places.isEmpty()) {
            return new OptimizedDayRoute(List.of(), List.of(), null, 0, 0);
        }

        OptimizedDayRoute best = null;
        BusinessException lastFailure = null;
        for (List<Place> order : permutations(places)) {
            try {
                OptimizedDayRoute candidate = evaluate(day, order, routeResolver, preference);
                if (best == null || candidate.optimizationCost() < best.optimizationCost()) {
                    best = candidate;
                }
            } catch (BusinessException exception) {
                lastFailure = exception;
            }
        }
        if (best != null) {
            return best;
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("No route permutation was evaluated");
    }

    private OptimizedDayRoute evaluate(
            ScheduleDay day,
            List<Place> order,
            RouteResolver routeResolver,
            OptimizationPreference preference
    ) {
        TransitPoint previous = new TransitPoint(
                day.getStartPlaceName(),
                day.getStartLongitude(),
                day.getStartLatitude()
        );
        List<TransitRouteResult> inboundRoutes = new ArrayList<>();
        int totalMinutes = 0;
        int optimizationCost = 0;
        for (Place place : order) {
            TransitPoint destination = point(place);
            TransitRouteResult route = routeResolver.resolve(previous, destination);
            inboundRoutes.add(route);
            totalMinutes += route.totalMinutes();
            optimizationCost += routeCost(route, preference);
            previous = destination;
        }
        TransitRouteResult finalRoute = routeResolver.resolve(previous, new TransitPoint(
                day.getEndPlaceName(),
                day.getEndLongitude(),
                day.getEndLatitude()
        ));
        totalMinutes += finalRoute.totalMinutes();
        optimizationCost += routeCost(finalRoute, preference);
        return new OptimizedDayRoute(
                List.copyOf(order),
                List.copyOf(inboundRoutes),
                finalRoute,
                totalMinutes,
                optimizationCost
        );
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
                + transfers * preference.transferPenaltyMinutes();
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
            int optimizationCost
    ) {
    }

    public record OptimizationPreference(
            int walkPenaltyMultiplier,
            int transferPenaltyMinutes
    ) {
    }
}
