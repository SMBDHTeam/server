package com.server.schedule.planner;

import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.util.List;
import java.util.Set;

/** Inputs shared by repair strategies. The current route order reflects the failed concrete route. */
public record ScheduleRepairContext(
        int failedDayIndex,
        List<List<Place>> placesByDay,
        List<Place> allCandidates,
        List<Place> currentOrder,
        List<ScheduleDay> days,
        List<PlaceCountPolicy> placeCountPolicies,
        Set<Long> mustVisitPlaceIds,
        Set<Long> fixedEventPlaceIds,
        ScheduleCreateRequest request,
        PlacePreferenceScorer preferenceScorer
) {

    public ScheduleRepairContext {
        placesByDay = placesByDay.stream().map(List::copyOf).toList();
        allCandidates = List.copyOf(allCandidates);
        currentOrder = List.copyOf(currentOrder);
        days = List.copyOf(days);
        placeCountPolicies = List.copyOf(placeCountPolicies);
        mustVisitPlaceIds = Set.copyOf(mustVisitPlaceIds);
        fixedEventPlaceIds = Set.copyOf(fixedEventPlaceIds);
    }

    public List<Place> failedDayPlaces() {
        return placesByDay.get(failedDayIndex);
    }

    public boolean isProtected(Place place) {
        return mustVisitPlaceIds.contains(place.getId()) || fixedEventPlaceIds.contains(place.getId());
    }

    public boolean isFixedEvent(Place place) {
        return fixedEventPlaceIds.contains(place.getId());
    }

    public ScheduleCreateRequest.Location startLocation() {
        ScheduleDay day = days.get(failedDayIndex);
        if (day.getStartLongitude() != null && day.getStartLatitude() != null) {
            return new ScheduleCreateRequest.Location(
                    day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
        }
        return request.startLocation();
    }

    public ScheduleCreateRequest.Location endLocation() {
        ScheduleDay day = days.get(failedDayIndex);
        if (day.getEndLongitude() != null && day.getEndLatitude() != null) {
            return new ScheduleCreateRequest.Location(
                    day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude());
        }
        return request.endLocation() == null ? startLocation() : request.endLocation();
    }
}
