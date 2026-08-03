package com.server.schedule.planner;

import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitSegment;
import com.server.schedule.domain.TransitRoute;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.planner.MobilityPreferencePolicy.LowWalkLevel;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScheduleFeasibilityChecker {

    private static final int MIN_STAY_MINUTES = 30;
    private static final int MAX_TRANSIT_MINUTES_PER_DAY = 150;
    private static final int MAX_TRANSIT_MINUTES_PER_LEG = 60;
    private static final int MAX_WALK_MINUTES_PER_LEG = 25;
    private static final int STRONG_LOW_WALK_MAX_MINUTES_PER_DAY = 45;
    private static final int STRONG_LOW_WALK_MAX_MINUTES_PER_LEG = 15;
    private static final int MODERATE_LOW_WALK_MAX_MINUTES_PER_DAY = 70;
    private static final int MODERATE_LOW_WALK_MAX_MINUTES_PER_LEG = 20;
    private static final int SIMPLE_TRANSIT_MAX_TRANSFERS_PER_DAY = 2;

    public boolean isWithinAvailableTime(ScheduleDay day) {
        long availableMinutes = Duration.between(day.getStartTime(), day.getEndTime()).toMinutes();
        return plannedMinutes(day) <= availableMinutes;
    }

    public boolean fitWithinAvailableTime(ScheduleDay day) {
        long availableMinutes = Duration.between(day.getStartTime(), day.getEndTime()).toMinutes();
        long overrunMinutes = plannedMinutes(day) - availableMinutes;
        if (overrunMinutes <= 0) {
            return true;
        }
        List<ScheduleStop> stops = day.getStops();
        for (int index = stops.size() - 1; index >= 0 && overrunMinutes > 0; index--) {
            ScheduleStop stop = stops.get(index);
            if (stop.getFixedStartsAt() != null) continue;
            int reducibleMinutes = Math.max(0, stop.getStayMinutes() - MIN_STAY_MINUTES);
            int reduction = (int) Math.min(overrunMinutes, reducibleMinutes);
            if (reduction > 0) {
                stop.updateStayMinutes(stop.getStayMinutes() - reduction);
                overrunMinutes -= reduction;
            }
        }
        return plannedMinutes(day) <= availableMinutes;
    }

    public long plannedMinutes(ScheduleDay day) {
        return activeMinutes(day) + MealTimePolicy.waitingMinutes(day);
    }

    public long activeMinutes(ScheduleDay day) {
        long activeMinutes = day.getStops().stream()
                .mapToLong(stop -> stop.getStayMinutes()
                        + (stop.getInboundTransit() == null ? 0 : stop.getInboundTransit().getTotalMinutes()))
                .sum();
        activeMinutes += day.getTransitRoutes().stream()
                .filter(route -> "FINAL".equals(route.getRouteType()))
                .mapToLong(TransitRoute::getTotalMinutes)
                .sum();
        return activeMinutes;
    }

    /**
     * Time feasibility alone is not enough: actual provider routes must also honor the
     * mobility and transit choices that were used to generate the itinerary.
     */
    public boolean fitsTravelPreferences(ScheduleDay day, ScheduleCreateRequest request) {
        List<TransitRoute> routes = day.getTransitRoutes().stream()
                .filter(this::isExternalProviderRoute)
                .toList();
        // Internal walking fallbacks and legacy/manual routes are retained for explicit
        // destinations, but cannot be evaluated with the same transit-quality rules.
        if (routes.isEmpty() || routes.stream().anyMatch(route -> route.getSegments().isEmpty())) {
            return true;
        }
        int totalTransitMinutes = routes.stream().mapToInt(TransitRoute::getTotalMinutes).sum();
        if (totalTransitMinutes > MAX_TRANSIT_MINUTES_PER_DAY
                || routes.stream().anyMatch(route -> route.getTotalMinutes() > MAX_TRANSIT_MINUTES_PER_LEG)) {
            return false;
        }

        LowWalkLevel lowWalkLevel = MobilityPreferencePolicy.lowWalkLevel(request);
        int maxWalkMinutesPerLeg = maxWalkMinutesPerLeg(lowWalkLevel);
        int totalWalkMinutes = routes.stream()
                .flatMap(route -> route.getSegments().stream())
                .filter(segment -> "WALK".equals(segment.getMode()))
                .mapToInt(TransitSegment::getDurationMinutes)
                .sum();
        boolean longWalkLeg = routes.stream()
                .flatMap(route -> route.getSegments().stream())
                .filter(segment -> "WALK".equals(segment.getMode()))
                .anyMatch(segment -> segment.getDurationMinutes() > maxWalkMinutesPerLeg);
        if (longWalkLeg || totalWalkMinutes > maxWalkMinutesPerDay(lowWalkLevel)) {
            return false;
        }

        if (MobilityPreferencePolicy.prefersSimpleTransit(request)) {
            int transferCount = routes.stream().mapToInt(this::transferCount).sum();
            return transferCount <= SIMPLE_TRANSIT_MAX_TRANSFERS_PER_DAY;
        }
        return true;
    }

    private int transferCount(TransitRoute route) {
        long transitSegments = route.getSegments().stream()
                .filter(segment -> !"WALK".equals(segment.getMode()))
                .count();
        return (int) Math.max(0, transitSegments - 1);
    }

    private boolean isExternalProviderRoute(TransitRoute route) {
        String provider = route.getProvider();
        return provider != null && !provider.isBlank()
                && !"UNKNOWN".equals(provider)
                && !"INTERNAL_WALK".equals(provider);
    }

    private int maxWalkMinutesPerLeg(LowWalkLevel level) {
        return switch (level) {
            case STRONG -> STRONG_LOW_WALK_MAX_MINUTES_PER_LEG;
            case MODERATE -> MODERATE_LOW_WALK_MAX_MINUTES_PER_LEG;
            case NONE -> MAX_WALK_MINUTES_PER_LEG;
        };
    }

    private int maxWalkMinutesPerDay(LowWalkLevel level) {
        return switch (level) {
            case STRONG -> STRONG_LOW_WALK_MAX_MINUTES_PER_DAY;
            case MODERATE -> MODERATE_LOW_WALK_MAX_MINUTES_PER_DAY;
            case NONE -> Integer.MAX_VALUE;
        };
    }
}
