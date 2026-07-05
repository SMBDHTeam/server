package com.server.schedule.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitRoute;
import com.server.schedule.domain.TransitRouteLine;
import com.server.schedule.domain.TransitSegment;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.repository.ScheduleRepository;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteProvider;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private static final int DEFAULT_STAY_MINUTES = 60;
    private static final Pattern COORDINATE_PAIR_PATTERN = Pattern.compile(
            "\\[\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?\\s*,\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?\\s*]"
    );

    private final ScheduleRepository scheduleRepository;
    private final PlaceRepository placeRepository;
    private final TransitRouteProvider transitRouteProvider;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            PlaceRepository placeRepository,
            TransitRouteProvider transitRouteProvider
    ) {
        this.scheduleRepository = scheduleRepository;
        this.placeRepository = placeRepository;
        this.transitRouteProvider = transitRouteProvider;
    }

    @Transactional
    public ScheduleResponse create(ScheduleCreateRequest request) {
        validateDateRange(request);
        List<Place> places = resolvePlaces(request.mustVisitPlaceIdsOrEmpty(), tripDays(request));
        Schedule schedule = new Schedule(
                request.startDate(),
                request.endDate(),
                request.dailyStartTime(),
                request.dailyEndTime(),
                request.startLocation().name(),
                request.startLocation().longitude(),
                request.startLocation().latitude(),
                request.endLocation().name(),
                request.endLocation().longitude(),
                request.endLocation().latitude(),
                styleSummary(request),
                conditionJson(request)
        );

        List<ScheduleDay> days = createDays(schedule);
        createStopsAndRoutes(schedule, days, places, request);

        scheduleRepository.save(schedule);
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public ScheduleMapResponse getMap(UUID scheduleId, Integer dayNo) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        List<ScheduleDay> days = schedule.getDays()
                .stream()
                .filter(day -> dayNo == null || day.getDayNo() == dayNo)
                .toList();

        return new ScheduleMapResponse(
                new ScheduleMapResponse.Marker(
                        schedule.getStartPlaceName(),
                        schedule.getStartLongitude(),
                        schedule.getStartLatitude()
                ),
                new ScheduleMapResponse.Marker(
                        schedule.getEndPlaceName(),
                        schedule.getEndLongitude(),
                        schedule.getEndLatitude()
                ),
                days.stream()
                        .flatMap(day -> day.getStops()
                                .stream()
                                .map(stop -> toStopMarker(day, stop)))
                        .toList(),
                days.stream()
                        .flatMap(day -> day.getTransitRoutes()
                                .stream()
                                .flatMap(route -> toRouteLines(schedule, day, route).stream()))
                        .toList()
        );
    }

    private void validateDateRange(ScheduleCreateRequest request) {
        if (request.endDate().isBefore(request.startDate())
                || !request.dailyEndTime().isAfter(request.dailyStartTime())) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
        }
    }

    private int tripDays(ScheduleCreateRequest request) {
        return (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
    }

    private List<Place> resolvePlaces(List<Long> mustVisitPlaceIds, int tripDays) {
        if (mustVisitPlaceIds.isEmpty()) {
            List<Place> candidatePlaces = placeRepository.findAll()
                    .stream()
                    .sorted(Comparator.comparing(Place::getId))
                    .limit(tripDays)
                    .toList();
            if (candidatePlaces.isEmpty()) {
                throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
            }
            return candidatePlaces;
        }

        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(mustVisitPlaceIds));
        List<Place> places = placeRepository.findAllById(distinctIds);
        Map<Long, Place> placeById = places.stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        List<Place> orderedPlaces = new ArrayList<>();
        for (Long placeId : distinctIds) {
            Place place = placeById.get(placeId);
            if (place == null) {
                throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
            }
            orderedPlaces.add(place);
        }
        return orderedPlaces;
    }

    private List<ScheduleDay> createDays(Schedule schedule) {
        List<ScheduleDay> days = new ArrayList<>();
        LocalDate date = schedule.getStartDate();
        int dayNo = 1;
        while (!date.isAfter(schedule.getEndDate())) {
            days.add(new ScheduleDay(schedule, dayNo, date));
            date = date.plusDays(1);
            dayNo++;
        }
        return days;
    }

    private void createStopsAndRoutes(
            Schedule schedule,
            List<ScheduleDay> days,
            List<Place> places,
            ScheduleCreateRequest request
    ) {
        List<List<ScheduleStop>> stopsByDay = new ArrayList<>();
        for (int index = 0; index < days.size(); index++) {
            stopsByDay.add(new ArrayList<>());
        }

        for (int index = 0; index < places.size(); index++) {
            ScheduleDay day = days.get(index % days.size());
            List<ScheduleStop> dayStops = stopsByDay.get(index % days.size());
            ScheduleStop stop = new ScheduleStop(day, places.get(index), dayStops.size() + 1, DEFAULT_STAY_MINUTES);
            dayStops.add(stop);
        }

        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            createRoutesForDay(schedule, days.get(dayIndex), stopsByDay.get(dayIndex), request);
        }
    }

    private void createRoutesForDay(
            Schedule schedule,
            ScheduleDay day,
            List<ScheduleStop> stops,
            ScheduleCreateRequest request
    ) {
        if (stops.isEmpty()) {
            return;
        }

        TransitPoint previousPoint = new TransitPoint(
                schedule.getStartPlaceName(),
                schedule.getStartLongitude(),
                schedule.getStartLatitude()
        );
        int routeOrder = 1;
        for (ScheduleStop stop : stops) {
            TransitRouteResult routeResult = transitRouteProvider.findRoute(previousPoint, point(stop.getPlace()));
            createRoute(day, stop, "INBOUND", routeOrder, routeResult);
            previousPoint = point(stop.getPlace());
            routeOrder++;
        }

        TransitRouteResult finalRoute = transitRouteProvider.findRoute(previousPoint, new TransitPoint(
                request.endLocation().name(),
                request.endLocation().longitude(),
                request.endLocation().latitude()
        ));
        createRoute(day, null, "FINAL", routeOrder, finalRoute);
    }

    private void createRoute(
            ScheduleDay day,
            ScheduleStop stop,
            String routeType,
            int routeOrder,
            TransitRouteResult result
    ) {
        TransitRoute route = new TransitRoute(
                day,
                stop,
                routeType,
                routeOrder,
                result.totalMinutes(),
                result.fareAmount(),
                result.rawJson()
        );
        for (int index = 0; index < result.segments().size(); index++) {
            TransitRouteResult.Segment segment = result.segments().get(index);
            new TransitSegment(
                    route,
                    index + 1,
                    segment.mode(),
                    segment.lineName(),
                    segment.startStationName(),
                    segment.endStationName()
            );
        }
        for (int index = 0; index < result.routeLines().size(); index++) {
            TransitRouteResult.RouteLine routeLine = result.routeLines().get(index);
            new TransitRouteLine(
                    route,
                    index + 1,
                    routeLine.mode(),
                    routeLine.lineName(),
                    routeLine.coordinatesJson()
            );
        }
    }

    private TransitPoint point(Place place) {
        return new TransitPoint(place.getName(), place.getLongitude(), place.getLatitude());
    }

    private String styleSummary(ScheduleCreateRequest request) {
        return request.selectedAnswers()
                .stream()
                .map(answer -> answer.questionId() + ":" + answer.answerId())
                .collect(Collectors.joining(", "));
    }

    private String conditionJson(ScheduleCreateRequest request) {
        String selectedAnswersJson = request.selectedAnswers()
                .stream()
                .map(answer -> "{\"questionId\":\"" + escapeJson(answer.questionId())
                        + "\",\"answerId\":\"" + escapeJson(answer.answerId()) + "\"}")
                .collect(Collectors.joining(","));
        String mustVisitPlaceIdsJson = request.mustVisitPlaceIdsOrEmpty()
                .stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return "{\"selectedAnswers\":["
                + selectedAnswersJson
                + "],\"mustVisitPlaceIds\":["
                + mustVisitPlaceIdsJson
                + "]}";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getStatus(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getStyleSummary(),
                schedule.getDays()
                        .stream()
                        .map(this::toDayResponse)
                        .toList()
        );
    }

    private ScheduleResponse.Day toDayResponse(ScheduleDay day) {
        return new ScheduleResponse.Day(
                day.getDayNo(),
                day.getDate(),
                day.getStops()
                        .stream()
                        .map(this::toStopResponse)
                        .toList(),
                day.getTransitRoutes()
                        .stream()
                        .filter(route -> "FINAL".equals(route.getRouteType()))
                        .findFirst()
                        .map(this::toTransitResponse)
                        .orElse(null)
        );
    }

    private ScheduleResponse.Stop toStopResponse(ScheduleStop stop) {
        Place place = stop.getPlace();
        return new ScheduleResponse.Stop(
                stop.getId(),
                stop.getStopOrder(),
                stop.getStayMinutes(),
                new ScheduleResponse.Place(
                        place.getId(),
                        place.getName(),
                        place.getLongitude(),
                        place.getLatitude()
                ),
                stop.getInboundTransit() == null ? null : toTransitResponse(stop.getInboundTransit())
        );
    }

    private ScheduleResponse.Transit toTransitResponse(TransitRoute route) {
        return new ScheduleResponse.Transit(
                route.getTotalMinutes(),
                route.getFareAmount(),
                route.getSegments()
                        .stream()
                        .map(segment -> new ScheduleResponse.Segment(
                                segment.getMode(),
                                segment.getLineName(),
                                segment.getStartStationName(),
                                segment.getEndStationName()
                        ))
                        .toList()
        );
    }

    private ScheduleMapResponse.StopMarker toStopMarker(ScheduleDay day, ScheduleStop stop) {
        Place place = stop.getPlace();
        return new ScheduleMapResponse.StopMarker(
                day.getDayNo(),
                stop.getStopOrder(),
                place.getId(),
                place.getName(),
                place.getLongitude(),
                place.getLatitude()
        );
    }

    private List<ScheduleMapResponse.RouteLine> toRouteLines(Schedule schedule, ScheduleDay day, TransitRoute route) {
        List<ScheduleMapResponse.RouteLine> response = new ArrayList<>();
        List<TransitSegment> segments = route.getSegments();
        int segmentStartIndex = 0;
        for (TransitRouteLine routeLine : route.getRouteLines()) {
            SegmentMatch segmentMatch = nextMatchingSegment(segments, segmentStartIndex, routeLine.getMode());
            TransitSegment segment = segmentMatch.segment();
            segmentStartIndex = segmentMatch.nextIndex();
            int segmentOrder = segment == null ? routeLine.getLineOrder() : segment.getSegmentOrder();
            response.add(new ScheduleMapResponse.RouteLine(
                    day.getDayNo(),
                    route.getRouteOrder(),
                    routeLine.getLineOrder(),
                    routeLine.getMode(),
                    routeLine.getLineName(),
                    routeLineStartName(schedule, day, route, segment, segmentOrder),
                    routeLineEndName(schedule, day, route, segment, segmentOrder),
                    coordinates(routeLine.getCoordinatesJson())
            ));
        }
        return response;
    }

    private SegmentMatch nextMatchingSegment(List<TransitSegment> segments, int startIndex, String mode) {
        for (int index = startIndex; index < segments.size(); index++) {
            TransitSegment segment = segments.get(index);
            if (segment.getMode().equals(mode)) {
                return new SegmentMatch(segment, index + 1);
            }
        }
        return new SegmentMatch(null, startIndex);
    }

    private String routeLineStartName(
            Schedule schedule,
            ScheduleDay day,
            TransitRoute route,
            TransitSegment segment,
            int segmentOrder
    ) {
        if (segment != null && !"WALK".equals(segment.getMode())) {
            return firstNonBlank(segment.getStartStationName(), routeOriginName(schedule, day, route));
        }

        TransitSegment previousTransit = previousTransitSegment(route, segmentOrder);
        if (previousTransit != null) {
            return firstNonBlank(previousTransit.getEndStationName(), routeOriginName(schedule, day, route));
        }
        return routeOriginName(schedule, day, route);
    }

    private String routeLineEndName(
            Schedule schedule,
            ScheduleDay day,
            TransitRoute route,
            TransitSegment segment,
            int segmentOrder
    ) {
        if (segment != null && !"WALK".equals(segment.getMode())) {
            return firstNonBlank(segment.getEndStationName(), routeDestinationName(schedule, route));
        }

        TransitSegment nextTransit = nextTransitSegment(route, segmentOrder);
        if (nextTransit != null) {
            return firstNonBlank(nextTransit.getStartStationName(), routeDestinationName(schedule, route));
        }
        return routeDestinationName(schedule, route);
    }

    private TransitSegment previousTransitSegment(TransitRoute route, int lineOrder) {
        TransitSegment previous = null;
        for (TransitSegment segment : route.getSegments()) {
            if (segment.getSegmentOrder() >= lineOrder) {
                break;
            }
            if (!"WALK".equals(segment.getMode())) {
                previous = segment;
            }
        }
        return previous;
    }

    private TransitSegment nextTransitSegment(TransitRoute route, int lineOrder) {
        return route.getSegments()
                .stream()
                .filter(segment -> segment.getSegmentOrder() > lineOrder)
                .filter(segment -> !"WALK".equals(segment.getMode()))
                .findFirst()
                .orElse(null);
    }

    private String routeOriginName(Schedule schedule, ScheduleDay day, TransitRoute route) {
        if (route.getRouteOrder() == 1) {
            return schedule.getStartPlaceName();
        }
        int previousStopOrder = route.getRouteOrder() - 1;
        return day.getStops()
                .stream()
                .filter(stop -> stop.getStopOrder() == previousStopOrder)
                .findFirst()
                .map(stop -> stop.getPlace().getName())
                .orElse(schedule.getStartPlaceName());
    }

    private String routeDestinationName(Schedule schedule, TransitRoute route) {
        if ("FINAL".equals(route.getRouteType()) || route.getScheduleStop() == null) {
            return schedule.getEndPlaceName();
        }
        return route.getScheduleStop().getPlace().getName();
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private record SegmentMatch(TransitSegment segment, int nextIndex) {
    }

    private List<List<BigDecimal>> coordinates(String coordinatesJson) {
        if (coordinatesJson == null || coordinatesJson.isBlank()) {
            return List.of();
        }
        List<List<BigDecimal>> coordinates = new ArrayList<>();
        Matcher matcher = COORDINATE_PAIR_PATTERN.matcher(coordinatesJson);
        while (matcher.find()) {
            coordinates.add(List.of(new BigDecimal(matcher.group(1)), new BigDecimal(matcher.group(2))));
        }
        return coordinates;
    }
}
