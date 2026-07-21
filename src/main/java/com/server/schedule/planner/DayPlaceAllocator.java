package com.server.schedule.planner;

import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DayPlaceAllocator {

    private static final int FILL_BALANCE_PENALTY_METERS = 20_000;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public List<List<Place>> allocate(
            List<Place> places,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets
    ) {
        List<List<Place>> placesByDay = new ArrayList<>();
        days.forEach(ignored -> placesByDay.add(new ArrayList<>()));
        for (Place place : places) {
            int dayIndex = bestDay(place, days, dailyStopTargets, placesByDay);
            placesByDay.get(dayIndex).add(place);
        }
        return placesByDay.stream().map(List::copyOf).toList();
    }

    private int bestDay(
            Place place,
            List<ScheduleDay> days,
            List<Integer> dailyStopTargets,
            List<List<Place>> placesByDay
    ) {
        int bestDayIndex = -1;
        long bestCost = Long.MAX_VALUE;
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            int target = dailyStopTargets.get(dayIndex);
            int assigned = placesByDay.get(dayIndex).size();
            if (assigned >= target) {
                continue;
            }
            ScheduleDay day = days.get(dayIndex);
            long endpointCost = 0;
            if (day.getStartLongitude() != null) {
                endpointCost += distanceMeters(day.getStartLongitude(), day.getStartLatitude(),
                        place.getLongitude(), place.getLatitude());
            }
            if (day.getEndLongitude() != null) {
                endpointCost += distanceMeters(place.getLongitude(), place.getLatitude(),
                        day.getEndLongitude(), day.getEndLatitude());
            }
            long balanceCost = (long) assigned * FILL_BALANCE_PENALTY_METERS / Math.max(1, target);
            long totalCost = endpointCost + balanceCost;
            if (totalCost < bestCost) {
                bestCost = totalCost;
                bestDayIndex = dayIndex;
            }
        }
        if (bestDayIndex >= 0) {
            return bestDayIndex;
        }
        return leastLoadedDay(placesByDay);
    }

    private int leastLoadedDay(List<List<Place>> placesByDay) {
        int bestDayIndex = 0;
        for (int dayIndex = 1; dayIndex < placesByDay.size(); dayIndex++) {
            if (placesByDay.get(dayIndex).size() < placesByDay.get(bestDayIndex).size()) {
                bestDayIndex = dayIndex;
            }
        }
        return bestDayIndex;
    }

    private int distanceMeters(
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
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }
}
