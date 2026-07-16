package com.server.schedule.planner;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitRoute;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FixedEventPlanner {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public List<List<Place>> placeOnRequiredDays(
            List<List<Place>> allocated,
            List<ScheduleDay> days,
            List<Integer> targets,
            Map<LocalDate, Set<Long>> fixedByDate
    ) {
        if (fixedByDate.isEmpty()) return allocated;
        List<List<Place>> result = allocated.stream()
                .<List<Place>>map(places -> new ArrayList<>(places))
                .toList();
        for (int targetIndex = 0; targetIndex < days.size(); targetIndex++) {
            Set<Long> fixedIds = fixedByDate.getOrDefault(days.get(targetIndex).getDate(), Set.of());
            for (Long fixedId : fixedIds) {
                Move move = find(result, fixedId);
                if (move.place() == null || move.sourceIndex() == targetIndex) continue;
                result.get(move.sourceIndex()).remove(move.place());
                if (result.get(targetIndex).size() >= targets.get(targetIndex)) {
                    Place displaced = result.get(targetIndex).stream()
                            .filter(place -> !fixedIds.contains(place.getId()))
                            .findFirst()
                            .orElseThrow(() -> new BusinessException(ErrorCode.MUST_VISIT_PLACE_LIMIT_EXCEEDED));
                    result.get(targetIndex).remove(displaced);
                    result.get(move.sourceIndex()).add(displaced);
                }
                result.get(targetIndex).add(move.place());
            }
        }
        return result.stream().map(List::copyOf).toList();
    }

    public List<Place> optimizeOrder(
            ScheduleDay day,
            List<Place> routeOrder,
            Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId,
            DayRouteOptimizer.RouteResolver routeResolver,
            ToIntFunction<Place> stayMinutes
    ) {
        boolean hasFixedEvent = routeOrder.stream()
                .anyMatch(place -> fixedEventByPlaceId.containsKey(place.getId()));
        if (!hasFixedEvent) return routeOrder;
        List<Place> best = null;
        int bestCost = Integer.MAX_VALUE;
        for (List<Place> order : permutations(routeOrder)) {
            int transitMinutes = transitMinutes(
                    day, order, fixedEventByPlaceId, routeResolver, stayMinutes);
            int routeCost = transitMinutes < 0 ? -1
                    : transitMinutes + DayRouteOptimizer.routeFlow(day, order).totalPenalty();
            if (routeCost >= 0 && routeCost < bestCost) {
                best = order;
                bestCost = routeCost;
            }
        }
        if (best == null) throw new BusinessException(ErrorCode.FIXED_EVENT_UNREACHABLE);
        return best;
    }

    public void validateDetailedFeasibility(List<ScheduleDay> days) {
        for (ScheduleDay day : days) {
            Map<UUID, TransitRoute> inboundByStop = day.getTransitRoutes().stream()
                    .filter(route -> route.getScheduleStop() != null)
                    .collect(Collectors.toMap(
                            route -> route.getScheduleStop().getId(), Function.identity()));
            LocalTime cursor = day.getStartTime();
            for (ScheduleStop stop : day.getStops()) {
                TransitRoute route = inboundByStop.get(stop.getId());
                if (route != null) cursor = cursor.plusMinutes(route.getTotalMinutes());
                if (stop.getFixedStartsAt() != null) {
                    LocalTime fixedStart = stop.getFixedStartsAt().atZoneSameInstant(SERVICE_ZONE).toLocalTime();
                    if (cursor.isAfter(fixedStart)) {
                        throw new BusinessException(ErrorCode.FIXED_EVENT_UNREACHABLE);
                    }
                    cursor = stop.getFixedEndsAt().atZoneSameInstant(SERVICE_ZONE).toLocalTime();
                } else {
                    cursor = cursor.plusMinutes(stop.getStayMinutes());
                }
            }
            int finalMinutes = day.getTransitRoutes().stream()
                    .filter(route -> "FINAL".equals(route.getRouteType()))
                    .mapToInt(TransitRoute::getTotalMinutes)
                    .findFirst().orElse(0);
            cursor = cursor.plusMinutes(finalMinutes);
            if (cursor.isAfter(day.getEndTime())) {
                throw new BusinessException("END_CONSTRAINT".equals(day.getEndLocationSource())
                        ? ErrorCode.END_CONSTRAINT_UNREACHABLE : ErrorCode.FIXED_EVENT_UNREACHABLE);
            }
        }
    }

    private int transitMinutes(
            ScheduleDay day,
            List<Place> order,
            Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId,
            DayRouteOptimizer.RouteResolver routeResolver,
            ToIntFunction<Place> stayMinutes
    ) {
        LocalTime cursor = day.getStartTime();
        TransitPoint previous = day.getStartLongitude() == null ? null : new TransitPoint(
                day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
        int transitMinutes = 0;
        try {
            for (Place place : order) {
                TransitPoint destination = new TransitPoint(
                        place.getName(), place.getLongitude(), place.getLatitude());
                if (previous != null) {
                    TransitRouteResult route = routeResolver.resolve(previous, destination);
                    transitMinutes += route.totalMinutes();
                    cursor = cursor.plusMinutes(route.totalMinutes());
                }
                SchedulePreviewCreateRequest.FixedEvent event = fixedEventByPlaceId.get(place.getId());
                if (event == null) {
                    cursor = cursor.plusMinutes(stayMinutes.applyAsInt(place));
                } else {
                    LocalTime startsAt = OffsetDateTime.parse(event.startsAt())
                            .atZoneSameInstant(SERVICE_ZONE).toLocalTime();
                    if (cursor.isAfter(startsAt)) return -1;
                    cursor = OffsetDateTime.parse(event.endsAt())
                            .atZoneSameInstant(SERVICE_ZONE).toLocalTime();
                }
                previous = destination;
            }
            if (day.getEndLongitude() != null && previous != null) {
                TransitRouteResult route = routeResolver.resolve(previous, new TransitPoint(
                        day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude()));
                transitMinutes += route.totalMinutes();
                cursor = cursor.plusMinutes(route.totalMinutes());
            }
            return cursor.isAfter(day.getEndTime()) ? -1 : transitMinutes;
        } catch (BusinessException exception) {
            return -1;
        }
    }

    private Move find(List<List<Place>> placesByDay, Long fixedId) {
        for (int index = 0; index < placesByDay.size(); index++) {
            for (Place place : placesByDay.get(index)) {
                if (Objects.equals(place.getId(), fixedId)) return new Move(index, place);
            }
        }
        return new Move(-1, null);
    }

    private List<List<Place>> permutations(List<Place> places) {
        List<List<Place>> result = new ArrayList<>();
        collect(new ArrayList<>(), new ArrayList<>(places), result);
        return result;
    }

    private void collect(List<Place> prefix, List<Place> remaining, List<List<Place>> result) {
        if (remaining.isEmpty()) {
            result.add(List.copyOf(prefix));
            return;
        }
        for (int index = 0; index < remaining.size(); index++) {
            Place place = remaining.remove(index);
            prefix.add(place);
            collect(prefix, remaining, result);
            prefix.remove(prefix.size() - 1);
            remaining.add(index, place);
        }
    }

    private record Move(int sourceIndex, Place place) { }
}
