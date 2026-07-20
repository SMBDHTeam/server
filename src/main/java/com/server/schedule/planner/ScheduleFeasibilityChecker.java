package com.server.schedule.planner;

import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitRoute;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScheduleFeasibilityChecker {

    private static final int MIN_STAY_MINUTES = 30;

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
}
