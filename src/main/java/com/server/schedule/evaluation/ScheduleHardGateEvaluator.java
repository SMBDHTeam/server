package com.server.schedule.evaluation;

import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitRoute;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ScheduleHardGateEvaluator {

    public ScheduleHardGateResult evaluate(Schedule schedule, Set<Long> mustVisitPlaceIds) {
        List<String> violations = new ArrayList<>();
        validateMustVisitPlaces(schedule, mustVisitPlaceIds, violations);
        schedule.getDays().forEach(day -> validateDay(day, violations));
        return new ScheduleHardGateResult(violations.isEmpty(), List.copyOf(violations));
    }

    private void validateMustVisitPlaces(
            Schedule schedule,
            Set<Long> mustVisitPlaceIds,
            List<String> violations
    ) {
        Set<Long> scheduledPlaceIds = new HashSet<>();
        schedule.getDays().stream()
                .flatMap(day -> day.getStops().stream())
                .map(ScheduleStop::getPlace)
                .forEach(place -> scheduledPlaceIds.add(place.getId()));
        if (!scheduledPlaceIds.containsAll(mustVisitPlaceIds)) {
            violations.add("MUST_VISIT_PLACE_MISSING");
        }
    }

    private void validateDay(ScheduleDay day, List<String> violations) {
        if (day.getStops().isEmpty()) {
            violations.add("EMPTY_DAY:" + day.getDayNo());
            return;
        }
        long availableMinutes = Duration.between(day.getStartTime(), day.getEndTime()).toMinutes();
        long plannedMinutes = day.getStops().stream()
                .mapToLong(stop -> stop.getStayMinutes()
                        + (stop.getInboundTransit() == null ? 0 : stop.getInboundTransit().getTotalMinutes()))
                .sum();
        List<TransitRoute> finalRoutes = day.getTransitRoutes().stream()
                .filter(route -> "FINAL".equals(route.getRouteType()))
                .toList();
        if (finalRoutes.size() != 1) {
            violations.add("FINAL_ROUTE_MISSING:" + day.getDayNo());
        } else {
            plannedMinutes += finalRoutes.get(0).getTotalMinutes();
        }
        boolean inboundRouteMissing = day.getStops().stream()
                .anyMatch(stop -> stop.getInboundTransit() == null);
        if (inboundRouteMissing) {
            violations.add("INBOUND_ROUTE_MISSING:" + day.getDayNo());
        }
        if (plannedMinutes > availableMinutes) {
            violations.add("DAY_TIME_OVERRUN:" + day.getDayNo());
        }
    }
}
