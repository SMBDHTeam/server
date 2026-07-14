package com.server.schedule.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.metrics.ExternalCallMetricsCollector;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitRoute;
import com.server.schedule.domain.TransitRouteLine;
import com.server.schedule.domain.TransitSegment;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleEvaluationReport;
import com.server.schedule.dto.ScheduleListResponse;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import com.server.schedule.evaluation.ScheduleHardGateEvaluator;
import com.server.schedule.evaluation.ScheduleHardGateResult;
import com.server.schedule.evaluation.ScheduleScoreEvaluator;
import com.server.schedule.evaluation.ScheduleScoreResult;
import com.server.schedule.planner.DayPlaceAllocator;
import com.server.schedule.planner.DayRouteOptimizer;
import com.server.schedule.planner.ScheduleFeasibilityChecker;
import com.server.schedule.planner.PlacePreferenceScorer;
import com.server.schedule.planner.PlaceCandidateProvider;
import com.server.schedule.planner.MultiDayPlanOptimizer;
import com.server.schedule.repository.ScheduleRepository;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteEstimate;
import com.server.transit.service.TransitRouteProvider;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private static final int DEFAULT_STAY_MINUTES = 60;
    private static final int MAX_STOPS_PER_DAY = 3;
    private static final int MIN_STOPS_PER_DAY = 1;
    private static final int ESTIMATED_TRANSIT_MINUTES_PER_STOP = 45;
    private static final int CLOSE_WALK_THRESHOLD_METERS = 1_200;
    private static final int PROVIDER_FAILURE_WALK_FALLBACK_METERS = 1_500;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final Pattern COORDINATE_PAIR_PATTERN = Pattern.compile(
            "\\[\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?\\s*,\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?\\s*]"
    );

    private final ScheduleRepository scheduleRepository;
    private final PlaceRepository placeRepository;
    private final TransitRouteProvider transitRouteProvider;
    private final ScheduleRequestValidator requestValidator;
    private final DayRouteOptimizer dayRouteOptimizer;
    private final SchedulePersistenceService persistenceService;
    private final ScheduleHardGateEvaluator hardGateEvaluator;
    private final ScheduleScoreEvaluator scoreEvaluator;
    private final MultiDayPlanOptimizer multiDayPlanOptimizer;
    private final ScheduleFeasibilityChecker feasibilityChecker;
    private final PlacePreferenceScorer placePreferenceScorer;
    private final PlaceCandidateProvider placeCandidateProvider;
    private final ExternalCallMetricsCollector externalCallMetricsCollector;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            PlaceRepository placeRepository,
            TransitRouteProvider transitRouteProvider
    ) {
        this(
                scheduleRepository,
                placeRepository,
                transitRouteProvider,
                new ScheduleRequestValidator(),
                new DayRouteOptimizer(),
                new SchedulePersistenceService(scheduleRepository),
                new ScheduleHardGateEvaluator(),
                new ScheduleScoreEvaluator(),
                new MultiDayPlanOptimizer(new DayPlaceAllocator(), new PlacePreferenceScorer()),
                new ScheduleFeasibilityChecker(),
                new PlacePreferenceScorer(),
                new PlaceCandidateProvider(placeRepository, new PlacePreferenceScorer()),
                new ExternalCallMetricsCollector()
        );
    }

    @Autowired
    public ScheduleService(
            ScheduleRepository scheduleRepository,
            PlaceRepository placeRepository,
            TransitRouteProvider transitRouteProvider,
            ScheduleRequestValidator requestValidator,
            DayRouteOptimizer dayRouteOptimizer,
            SchedulePersistenceService persistenceService,
            ScheduleHardGateEvaluator hardGateEvaluator,
            ScheduleScoreEvaluator scoreEvaluator,
            MultiDayPlanOptimizer multiDayPlanOptimizer,
            ScheduleFeasibilityChecker feasibilityChecker,
            PlacePreferenceScorer placePreferenceScorer,
            PlaceCandidateProvider placeCandidateProvider,
            ExternalCallMetricsCollector externalCallMetricsCollector
    ) {
        this.scheduleRepository = scheduleRepository;
        this.placeRepository = placeRepository;
        this.transitRouteProvider = transitRouteProvider;
        this.requestValidator = requestValidator;
        this.dayRouteOptimizer = dayRouteOptimizer;
        this.persistenceService = persistenceService;
        this.hardGateEvaluator = hardGateEvaluator;
        this.scoreEvaluator = scoreEvaluator;
        this.multiDayPlanOptimizer = multiDayPlanOptimizer;
        this.feasibilityChecker = feasibilityChecker;
        this.placePreferenceScorer = placePreferenceScorer;
        this.placeCandidateProvider = placeCandidateProvider;
        this.externalCallMetricsCollector = externalCallMetricsCollector;
    }

    public ScheduleResponse create(ScheduleCreateRequest request) {
        try (ExternalCallMetricsCollector.Scope externalMetrics = externalCallMetricsCollector.start()) {
        long startedAt = System.nanoTime();
        PlannerExecutionMetrics executionMetrics = new PlannerExecutionMetrics();
        requestValidator.validate(request);
        int tripDays = tripDays(request);
        List<Integer> dailyStopTargets = dailyStopTargets(request, tripDays);
        PlaceCandidateProvider.ResolvedPlaces resolvedPlaces = placeCandidateProvider.resolve(request, dailyStopTargets);
        ScheduleCreateRequest.Location overallStart = overallStartLocation(request);
        ScheduleCreateRequest.Location overallEnd = overallEndLocation(request, tripDays);
        Schedule schedule = new Schedule(
                request.startDate(),
                request.endDate(),
                request.dailyStartTime(),
                request.dailyEndTime(),
                overallStart.name(),
                overallStart.longitude(),
                overallStart.latitude(),
                overallEnd.name(),
                overallEnd.longitude(),
                overallEnd.latitude(),
                styleSummary(request),
                conditionJson(request)
        );

        List<ScheduleDay> days = createDays(schedule, request);
        createStopsAndRoutes(days, resolvedPlaces, dailyStopTargets, request, executionMetrics);
        ScheduleHardGateResult hardGateResult = hardGateEvaluator.evaluate(
                schedule,
                resolvedPlaces.mustVisitPlaceIds()
        );
        if (!hardGateResult.passed()) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
        }

        persistenceService.save(schedule);
        ScheduleResponse response = toResponse(schedule);
        ScheduleScoreResult scoreResult = scoreEvaluator.evaluate(request, response);
        long generationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        return withEvaluation(
                response,
                evaluationReport(
                        hardGateResult,
                        scoreResult,
                        schedule,
                        response,
                        executionMetrics,
                        externalMetrics.snapshot(),
                        generationMillis
                )
        );
        }
    }

    @Transactional(readOnly = true)
    public ScheduleListResponse getAll() {
        return new ScheduleListResponse(scheduleRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(UUID scheduleId) {
        return toResponse(findSchedule(scheduleId));
    }

    @Transactional
    public ScheduleResponse update(UUID scheduleId, ScheduleUpdateRequest request) {
        Schedule schedule = findSchedule(scheduleId);
        Map<Integer, ScheduleDay> dayByNumber = schedule.getDays().stream()
                .collect(Collectors.toMap(ScheduleDay::getDayNo, Function.identity()));
        Map<UUID, ScheduleStop> existingStopById = schedule.getDays().stream()
                .flatMap(day -> day.getStops().stream())
                .collect(Collectors.toMap(ScheduleStop::getId, Function.identity()));
        validateUpdateRequest(request, dayByNumber, existingStopById);

        Map<UUID, ScheduleDay> currentDayByStopId = new HashMap<>();
        schedule.getDays().forEach(day -> day.getStops()
                .forEach(stop -> currentDayByStopId.put(stop.getId(), day)));
        schedule.getDays().forEach(ScheduleDay::clearTransitRoutes);

        int temporaryOrder = Integer.MAX_VALUE;
        for (ScheduleDay day : schedule.getDays()) {
            for (ScheduleStop stop : day.getStops()) {
                stop.reassign(day, temporaryOrder--, stop.getStayMinutes());
            }
        }
        scheduleRepository.flush();

        Set<UUID> retainedStopIds = request.stops().stream()
                .map(ScheduleUpdateRequest.Stop::stopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        schedule.getDays().forEach(day -> List.copyOf(day.getStops()).stream()
                .filter(stop -> !retainedStopIds.contains(stop.getId()))
                .forEach(day::removeStop));
        scheduleRepository.flush();

        Map<Long, Place> placeById = placesForUpdate(request);
        for (ScheduleUpdateRequest.Stop item : request.stops()) {
            ScheduleDay targetDay = dayByNumber.get(item.dayNo());
            if (item.stopId() != null) {
                ScheduleStop stop = existingStopById.get(item.stopId());
                ScheduleDay currentDay = currentDayByStopId.get(item.stopId());
                if (currentDay != targetDay) {
                    currentDay.removeStop(stop);
                    targetDay.addStop(stop);
                }
                stop.reassign(targetDay, item.order(), item.stayMinutes());
            } else {
                ScheduleStop stop = new ScheduleStop(
                        targetDay,
                        placeById.get(item.placeId()),
                        item.order(),
                        item.stayMinutes()
                );
                stop.updateDeliveryInfo(
                        jsonArray(List.of("사용자가 일정 수정에서 추가한 장소입니다.")),
                        "[]"
                );
            }
        }
        schedule.getDays().forEach(ScheduleDay::sortStops);
        recalculateRoutes(schedule);
        schedule.touch();
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public ScheduleMapResponse getMap(UUID scheduleId, Integer dayNo) {
        Schedule schedule = findSchedule(scheduleId);
        List<ScheduleDay> days = schedule.getDays()
                .stream()
                .filter(day -> dayNo == null || day.getDayNo() == dayNo)
                .toList();

        ScheduleDay firstDay = days.isEmpty() ? schedule.getDays().get(0) : days.get(0);
        ScheduleDay lastDay = days.isEmpty() ? schedule.getDays().get(schedule.getDays().size() - 1) : days.get(days.size() - 1);
        return new ScheduleMapResponse(
                new ScheduleMapResponse.Marker(
                        firstDay.getStartPlaceName(),
                        firstDay.getStartLongitude(),
                        firstDay.getStartLatitude()
                ),
                new ScheduleMapResponse.Marker(
                        lastDay.getEndPlaceName(),
                        lastDay.getEndLongitude(),
                        lastDay.getEndLatitude()
                ),
                days.stream()
                        .flatMap(day -> {
                            Map<UUID, StopTime> stopTimes = stopTimes(schedule, day);
                            return day.getStops()
                                    .stream()
                                    .map(stop -> toStopMarker(day, stop, stopTimes.get(stop.getId())));
                        })
                        .toList(),
                days.stream()
                        .flatMap(day -> day.getTransitRoutes()
                                .stream()
                                .flatMap(route -> toRouteLines(schedule, day, route).stream()))
                        .toList()
        );
    }

    private Schedule findSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void validateUpdateRequest(
            ScheduleUpdateRequest request,
            Map<Integer, ScheduleDay> dayByNumber,
            Map<UUID, ScheduleStop> existingStopById
    ) {
        Set<UUID> stopIds = new HashSet<>();
        Set<String> dayOrders = new HashSet<>();
        Map<Integer, Set<Integer>> ordersByDay = new HashMap<>();
        for (ScheduleUpdateRequest.Stop item : request.stops()) {
            boolean validReference = (item.stopId() == null) != (item.placeId() == null)
                    && (item.placeId() == null || item.placeId() > 0);
            if (!validReference
                    || item.dayNo() <= 0
                    || item.order() <= 0
                    || item.stayMinutes() < 30
                    || !dayByNumber.containsKey(item.dayNo())
                    || !dayOrders.add(item.dayNo() + ":" + item.order())
                    || (item.stopId() != null
                    && (!existingStopById.containsKey(item.stopId()) || !stopIds.add(item.stopId())))) {
                throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
            }
            ordersByDay.computeIfAbsent(item.dayNo(), ignored -> new HashSet<>()).add(item.order());
        }
        for (Integer dayNo : dayByNumber.keySet()) {
            Set<Integer> orders = ordersByDay.get(dayNo);
            if (orders == null || orders.isEmpty() || orders.size() > MAX_STOPS_PER_DAY) {
                throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
            }
            for (int order = 1; order <= orders.size(); order++) {
                if (!orders.contains(order)) {
                    throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
                }
            }
        }
    }

    private Map<Long, Place> placesForUpdate(ScheduleUpdateRequest request) {
        Set<Long> placeIds = request.stops().stream()
                .map(ScheduleUpdateRequest.Stop::placeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Place> placeById = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        if (!placeById.keySet().containsAll(placeIds)) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        return placeById;
    }

    private void recalculateRoutes(Schedule schedule) {
        Map<RouteKey, TransitRouteEstimate> routeCache = new HashMap<>();
        PlannerExecutionMetrics executionMetrics = new PlannerExecutionMetrics();
        for (ScheduleDay day : schedule.getDays()) {
            TransitPoint previous = new TransitPoint(
                    day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
            for (ScheduleStop stop : day.getStops()) {
                TransitPoint destination = new TransitPoint(
                        stop.getPlace().getName(),
                        stop.getPlace().getLongitude(),
                        stop.getPlace().getLatitude()
                );
                TransitRouteResult route = resolveRoute(
                        routeCache,
                        executionMetrics,
                        previous,
                        destination,
                        RouteResolutionMode.DETAILED,
                        null
                ).route();
                createRoute(day, stop, "INBOUND", stop.getStopOrder(), route);
                previous = destination;
            }
            TransitRouteResult finalRoute = resolveRoute(
                    routeCache,
                    executionMetrics,
                    previous,
                    new TransitPoint(
                            day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude()),
                    RouteResolutionMode.DETAILED,
                    null
            ).route();
            createRoute(day, null, "FINAL", day.getStops().size() + 1, finalRoute);
            if (!feasibilityChecker.fitWithinAvailableTime(day)) {
                throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
            }
        }
    }

    private int tripDays(ScheduleCreateRequest request) {
        return (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
    }

    private ScheduleCreateRequest.Location overallStartLocation(ScheduleCreateRequest request) {
        return request.daysOrEmpty()
                .stream()
                .filter(day -> day.dayNo() == 1)
                .map(ScheduleCreateRequest.DayCondition::startLocation)
                .findFirst()
                .orElse(request.startLocation());
    }

    private ScheduleCreateRequest.Location overallEndLocation(ScheduleCreateRequest request, int tripDays) {
        return request.daysOrEmpty()
                .stream()
                .filter(day -> day.dayNo() == tripDays)
                .map(ScheduleCreateRequest.DayCondition::endLocation)
                .findFirst()
                .orElse(request.endLocation());
    }

    private List<Integer> dailyStopTargets(ScheduleCreateRequest request, int tripDays) {
        Map<Integer, ScheduleCreateRequest.DayCondition> dayConditions = request.daysOrEmpty()
                .stream()
                .collect(Collectors.toMap(ScheduleCreateRequest.DayCondition::dayNo, Function.identity()));
        return IntStream.rangeClosed(1, tripDays)
                .mapToObj(dayNo -> {
                    ScheduleCreateRequest.DayCondition condition = dayConditions.get(dayNo);
                    LocalTime startTime = condition == null ? request.dailyStartTime() : condition.startTime();
                    LocalTime endTime = condition == null ? request.dailyEndTime() : condition.endTime();
                    return stopsForAvailableMinutes(Duration.between(startTime, endTime).toMinutes());
                })
                .toList();
    }

    private int stopsForAvailableMinutes(long availableMinutes) {
        int estimatedStopBudget = DEFAULT_STAY_MINUTES + ESTIMATED_TRANSIT_MINUTES_PER_STOP;
        int stops = (int) Math.max(MIN_STOPS_PER_DAY, availableMinutes / estimatedStopBudget);
        return Math.min(MAX_STOPS_PER_DAY, stops);
    }

    private List<ScheduleDay> createDays(Schedule schedule, ScheduleCreateRequest request) {
        Map<Integer, ScheduleCreateRequest.DayCondition> dayConditions = request.daysOrEmpty()
                .stream()
                .collect(Collectors.toMap(ScheduleCreateRequest.DayCondition::dayNo, Function.identity()));
        List<ScheduleDay> days = new ArrayList<>();
        LocalDate date = schedule.getStartDate();
        int dayNo = 1;
        while (!date.isAfter(schedule.getEndDate())) {
            ScheduleCreateRequest.DayCondition condition = dayConditions.get(dayNo);
            if (condition == null) {
                days.add(new ScheduleDay(schedule, dayNo, date));
            } else {
                days.add(new ScheduleDay(
                        schedule,
                        dayNo,
                        date,
                        condition.startTime(),
                        condition.endTime(),
                        condition.startLocation().name(),
                        condition.startLocation().longitude(),
                        condition.startLocation().latitude(),
                        condition.endLocation().name(),
                        condition.endLocation().longitude(),
                        condition.endLocation().latitude()
                ));
            }
            date = date.plusDays(1);
            dayNo++;
        }
        return days;
    }

    private void createStopsAndRoutes(
            List<ScheduleDay> days,
            PlaceCandidateProvider.ResolvedPlaces resolvedPlaces,
            List<Integer> dailyStopTargets,
            ScheduleCreateRequest request,
            PlannerExecutionMetrics executionMetrics
    ) {
        List<Place> places = resolvedPlaces.places();
        List<List<Place>> placesByDay = multiDayPlanOptimizer.optimize(
                places,
                resolvedPlaces.mustVisitPlaceIds(),
                days,
                dailyStopTargets,
                request
        );

        Map<RouteKey, TransitRouteEstimate> estimateCache = new HashMap<>();
        Map<RouteKey, TransitRouteEstimate> detailedCache = new HashMap<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            ScheduleDay day = days.get(dayIndex);
            DayRouteOptimizer.OptimizedDayRoute optimizedRoute = dayRouteOptimizer.optimize(
                    day,
                    placesByDay.get(dayIndex),
                    (origin, destination) -> resolveRoute(
                            estimateCache,
                            executionMetrics,
                            origin,
                            destination,
                            RouteResolutionMode.ESTIMATE,
                            null
                    ).route(),
                    optimizationPreference(request)
            );
            List<ScheduleStop> stops = new ArrayList<>();
            TransitPoint previous = new TransitPoint(
                    day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
            for (int stopIndex = 0; stopIndex < optimizedRoute.places().size(); stopIndex++) {
                Place place = optimizedRoute.places().get(stopIndex);
                TransitPoint destination = new TransitPoint(
                        place.getName(), place.getLongitude(), place.getLatitude());
                TransitRouteResult inboundRoute = selectedRoute(
                        estimateCache, detailedCache, executionMetrics, previous, destination);
                ScheduleStop stop = new ScheduleStop(day, place, stopIndex + 1, stayMinutes(place));
                stop.updateDeliveryInfo(
                        jsonArray(selectionReasons(
                                place,
                                !resolvedPlaces.mustVisitPlaceIds().contains(place.getId()),
                                request
                        )),
                        jsonArray(stopWarnings(place, request))
                );
                stops.add(stop);
                createRoute(day, stop, "INBOUND", stopIndex + 1, inboundRoute);
                previous = destination;
            }
            if (!optimizedRoute.places().isEmpty()) {
                TransitRouteResult finalRoute = selectedRoute(
                        estimateCache,
                        detailedCache,
                        executionMetrics,
                        previous,
                        new TransitPoint(day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude())
                );
                createRoute(day, null, "FINAL", stops.size() + 1, finalRoute);
            }
            feasibilityChecker.fitWithinAvailableTime(day);
        }
    }

    private TransitRouteResult selectedRoute(
            Map<RouteKey, TransitRouteEstimate> estimateCache,
            Map<RouteKey, TransitRouteEstimate> detailedCache,
            PlannerExecutionMetrics executionMetrics,
            TransitPoint origin,
            TransitPoint destination
    ) {
        TransitRouteEstimate estimate = estimateCache.get(new RouteKey(origin, destination));
        if (estimate != null && !estimate.requiresDetail()) {
            return estimate.route();
        }
        return resolveRoute(
                detailedCache,
                executionMetrics,
                origin,
                destination,
                RouteResolutionMode.DETAILED,
                estimate
        ).route();
    }

    private TransitRouteEstimate resolveRoute(
            Map<RouteKey, TransitRouteEstimate> routeCache,
            PlannerExecutionMetrics executionMetrics,
            TransitPoint origin,
            TransitPoint destination,
            RouteResolutionMode resolutionMode,
            TransitRouteEstimate estimate
    ) {
        executionMetrics.routeResolutionCount++;
        RouteKey key = new RouteKey(origin, destination);
        TransitRouteEstimate cached = routeCache.get(key);
        if (cached != null) {
            executionMetrics.routeCacheHitCount++;
            return cached;
        }
        TransitRouteEstimate result = routeBetween(
                origin,
                destination,
                executionMetrics,
                resolutionMode,
                estimate
        );
        routeCache.put(key, result);
        return result;
    }

    private DayRouteOptimizer.OptimizationPreference optimizationPreference(ScheduleCreateRequest request) {
        boolean lowWalkPreference = hasAnswer(request, "COMPANION_PARENTS")
                || hasAnswer(request, "COMPANION_FAMILY_WITH_CHILD")
                || hasAnswer(request, "MOBILITY_LOW_WALK")
                || hasAnswer(request, "MOBILITY_AVOID_HILLS_STAIRS");
        int walkPenaltyMultiplier = lowWalkPreference ? 2 : 0;
        int transferPenaltyMinutes = hasAnswer(request, "TRANSIT_SIMPLE") ? 20 : 0;
        return new DayRouteOptimizer.OptimizationPreference(walkPenaltyMultiplier, transferPenaltyMinutes);
    }

    private int stayMinutes(Place place) {
        if (Objects.equals(place.getContentTypeId(), "15")) {
            return 90;
        }
        if (Objects.equals(place.getContentTypeId(), "39")) {
            return 75;
        }
        return DEFAULT_STAY_MINUTES;
    }

    private boolean hasAnswer(ScheduleCreateRequest request, String answerId) {
        return request.selectedAnswers().stream()
                .anyMatch(answer -> answerId.equals(answer.answerId()));
    }

    private Optional<String> answerId(ScheduleCreateRequest request, String questionId) {
        return request.selectedAnswers().stream()
                .filter(answer -> questionId.equals(answer.questionId()))
                .map(ScheduleCreateRequest.SelectedAnswer::answerId)
                .filter(Objects::nonNull)
                .findFirst();
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
                result.provider(),
                result.realtimeStatus(),
                result.fallbackUsed(),
                jsonArray(result.warnings()),
                result.rawJson()
        );
        for (int index = 0; index < result.segments().size(); index++) {
            TransitRouteResult.Segment segment = result.segments().get(index);
            new TransitSegment(
                    route,
                    index + 1,
                    segment.mode(),
                    segment.lineName(),
                    segment.startStationId(),
                    segment.startStationName(),
                    segment.endStationId(),
                    segment.endStationName(),
                    instruction(segment),
                    segmentDuration(segment, result),
                    segment.distanceMeters(),
                    segment.stationCount(),
                    segment.waitMinutes(),
                    segment.realtimeStatus()
            );
        }
        for (int index = 0; index < result.routeLines().size(); index++) {
            TransitRouteResult.RouteLine routeLine = result.routeLines().get(index);
            new TransitRouteLine(
                    route,
                    index + 1,
                    routeLine.mode(),
                    routeLine.lineName(),
                    routeLine.coordinatesJson(),
                    routeLine.durationMinutes(),
                    routeLine.distanceMeters(),
                    routeLine.instruction(),
                    routeLine.fallbackUsed()
            );
        }
    }

    private TransitRouteEstimate routeBetween(
            TransitPoint origin,
            TransitPoint destination,
            PlannerExecutionMetrics executionMetrics,
            RouteResolutionMode resolutionMode,
            TransitRouteEstimate estimate
    ) {
        int distanceMeters = distanceMeters(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        );
        if (distanceMeters <= CLOSE_WALK_THRESHOLD_METERS) {
            return TransitRouteEstimate.detailed(walkRoute(origin, destination, distanceMeters, false));
        }
        try {
            executionMetrics.providerCallCount++;
            if (resolutionMode == RouteResolutionMode.ESTIMATE) {
                return transitRouteProvider.findRouteEstimate(origin, destination);
            }
            TransitRouteResult detailedRoute = estimate == null
                    ? transitRouteProvider.findRoute(origin, destination)
                    : transitRouteProvider.findRouteDetail(origin, destination, estimate);
            return TransitRouteEstimate.detailed(detailedRoute);
        } catch (BusinessException exception) {
            executionMetrics.providerFailureCount++;
            if (isFallbackEligible(exception) && distanceMeters <= PROVIDER_FAILURE_WALK_FALLBACK_METERS) {
                return TransitRouteEstimate.detailed(walkRoute(origin, destination, distanceMeters, true));
            }
            throw exception;
        }
    }

    private boolean isFallbackEligible(BusinessException exception) {
        return exception.getErrorCode() == ErrorCode.TRANSIT_ROUTE_NOT_FOUND
                || exception.getErrorCode() == ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE;
    }

    private TransitRouteResult walkRoute(
            TransitPoint origin,
            TransitPoint destination,
            int distanceMeters,
            boolean fallbackUsed
    ) {
        int minutes = Math.max(5, BigDecimal.valueOf(distanceMeters)
                .divide(BigDecimal.valueOf(70), 0, RoundingMode.UP)
                .intValue());
        String coordinatesJson = "[["
                + origin.longitude() + "," + origin.latitude()
                + "],["
                + destination.longitude() + "," + destination.latitude()
                + "]]";
        return new TransitRouteResult(
                minutes,
                null,
                "INTERNAL_WALK",
                "UNAVAILABLE",
                fallbackUsed,
                fallbackUsed
                        ? List.of(
                                "외부 경로 API 실패로 도보 fallback을 사용했습니다.",
                                "도보 경로는 실시간 보행 장애 정보를 반영하지 않습니다."
                        )
                        : List.of("도보 경로는 실시간 보행 장애 정보를 반영하지 않습니다."),
                List.of(new TransitRouteResult.Segment(
                        "WALK",
                        null,
                        null,
                        origin.name(),
                        null,
                        destination.name(),
                        origin.name() + "에서 " + destination.name() + "까지 도보 이동",
                        minutes,
                        distanceMeters,
                        null,
                        0,
                        "UNAVAILABLE"
                )),
                List.of(new TransitRouteResult.RouteLine(
                        "WALK",
                        null,
                        coordinatesJson,
                        minutes,
                        distanceMeters,
                        origin.name() + "에서 " + destination.name() + "까지 도보 이동",
                        fallbackUsed
                )),
                "{\"provider\":\"" + (fallbackUsed ? "WALK_FALLBACK" : "INTERNAL_WALK")
                        + "\",\"distanceMeters\":" + distanceMeters + "}"
        );
    }

    private int distanceMeters(BigDecimal fromLongitude, BigDecimal fromLatitude, BigDecimal toLongitude, BigDecimal toLatitude) {
        double fromLongitudeRadians = Math.toRadians(fromLongitude.doubleValue());
        double fromLatitudeRadians = Math.toRadians(fromLatitude.doubleValue());
        double toLongitudeRadians = Math.toRadians(toLongitude.doubleValue());
        double toLatitudeRadians = Math.toRadians(toLatitude.doubleValue());

        double deltaLongitude = toLongitudeRadians - fromLongitudeRadians;
        double deltaLatitude = toLatitudeRadians - fromLatitudeRadians;
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(fromLatitudeRadians) * Math.cos(toLatitudeRadians) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
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
        String daysJson = request.daysOrEmpty()
                .stream()
                .map(day -> "{\"dayNo\":" + day.dayNo()
                        + ",\"startTime\":\"" + day.startTime() + "\""
                        + ",\"endTime\":\"" + day.endTime() + "\""
                        + ",\"startLocation\":" + locationJson(day.startLocation())
                        + ",\"endLocation\":" + locationJson(day.endLocation()) + "}")
                .collect(Collectors.joining(","));
        return "{\"selectedAnswers\":["
                + selectedAnswersJson
                + "],\"mustVisitPlaceIds\":["
                + mustVisitPlaceIdsJson
                + "],\"days\":["
                + daysJson
                + "]}";
    }

    private String locationJson(ScheduleCreateRequest.Location location) {
        return "{\"name\":\"" + escapeJson(location.name())
                + "\",\"longitude\":" + location.longitude()
                + ",\"latitude\":" + location.latitude() + "}";
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
                schedule.getDailyStartTime(),
                schedule.getDailyEndTime(),
                schedule.getStyleSummary(),
                schedule.getDays()
                        .stream()
                        .map(day -> toDayResponse(schedule, day))
                        .toList()
        );
    }

    private ScheduleResponse withEvaluation(
            ScheduleResponse response,
            ScheduleEvaluationReport evaluation
    ) {
        return new ScheduleResponse(
                response.id(),
                response.status(),
                response.startDate(),
                response.endDate(),
                response.dailyStartTime(),
                response.dailyEndTime(),
                response.styleSummary(),
                response.days(),
                evaluation
        );
    }

    private ScheduleEvaluationReport evaluationReport(
            ScheduleHardGateResult hardGateResult,
            ScheduleScoreResult scoreResult,
            Schedule schedule,
            ScheduleResponse response,
            PlannerExecutionMetrics executionMetrics,
            ExternalCallMetricsCollector.Snapshot externalMetrics,
            long generationMillis
    ) {
        List<ScheduleResponse.Transit> routes = response.days().stream()
                .flatMap(day -> java.util.stream.Stream.concat(
                        day.stops().stream().map(ScheduleResponse.Stop::inboundTransit),
                        java.util.stream.Stream.of(day.finalTransit())
                ))
                .filter(Objects::nonNull)
                .toList();
        List<String> providers = routes.stream()
                .map(ScheduleResponse.Transit::provider)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        return new ScheduleEvaluationReport(
                new ScheduleEvaluationReport.HardGate(
                        hardGateResult.passed(),
                        hardGateResult.violations()
                ),
                new ScheduleEvaluationReport.QualityScore(
                        scoreResult.totalScore(),
                        scoreResult.metrics().stream().mapToInt(ScheduleScoreResult.Metric::maxScore).sum(),
                        scoreResult.metrics().stream()
                                .map(metric -> new ScheduleEvaluationReport.Metric(
                                        metric.id(),
                                        metric.label(),
                                        metric.score(),
                                        metric.maxScore(),
                                        metric.reason()
                                ))
                                .toList()
                ),
                new ScheduleEvaluationReport.Operations(
                        generationMillis,
                        executionMetrics.routeResolutionCount,
                        executionMetrics.routeCacheHitCount,
                        executionMetrics.providerCallCount,
                        executionMetrics.providerFailureCount,
                        externalMetrics.totalHttpCallCount(),
                        externalMetrics.failureCount(),
                        externalMetrics.odsayPathSearchCount(),
                        externalMetrics.odsayLoadLaneCount(),
                        externalMetrics.tmapWalkingCount(),
                        routes.size(),
                        (int) routes.stream().filter(ScheduleResponse.Transit::fallbackUsed).count(),
                        (int) schedule.getDays().stream()
                                .flatMap(day -> day.getTransitRoutes().stream())
                                .flatMap(route -> route.getRouteLines().stream())
                                .filter(TransitRouteLine::isFallbackUsed)
                                .count(),
                        routes.stream().mapToInt(ScheduleResponse.Transit::totalMinutes).sum(),
                        routes.stream().mapToInt(ScheduleResponse.Transit::walkMinutes).sum(),
                        routes.stream().mapToInt(ScheduleResponse.Transit::transferCount).sum(),
                        providers
                )
        );
    }

    private ScheduleResponse.Day toDayResponse(Schedule schedule, ScheduleDay day) {
        List<ScheduleResponse.Stop> stops = new ArrayList<>();
        Map<UUID, TransitRoute> inboundRouteByStopId = inboundRouteByStopId(day);
        LocalTime cursor = day.getStartTime();
        for (ScheduleStop stop : day.getStops()) {
            ScheduleResponse.Transit inboundTransit = null;
            TransitRoute inboundRoute = inboundRouteByStopId.get(stop.getId());
            if (inboundRoute != null) {
                inboundTransit = toTransitResponse(schedule, day, inboundRoute, cursor);
                cursor = cursor.plusMinutes(inboundRoute.getTotalMinutes());
            }
            LocalTime arriveAt = cursor;
            LocalTime departAt = arriveAt.plusMinutes(stop.getStayMinutes());
            stops.add(toStopResponse(stop, arriveAt, departAt, inboundTransit));
            cursor = departAt;
        }

        LocalTime finalTransitStart = cursor;
        ScheduleResponse.Transit finalTransit = day.getTransitRoutes()
                .stream()
                .filter(route -> "FINAL".equals(route.getRouteType()))
                .findFirst()
                .map(route -> toTransitResponse(schedule, day, route, finalTransitStart))
                .orElse(null);

        return new ScheduleResponse.Day(
                day.getDayNo(),
                day.getDate(),
                day.getStartTime(),
                day.getEndTime(),
                new ScheduleResponse.DayLocation(
                        day.getStartPlaceName(),
                        day.getStartLongitude(),
                        day.getStartLatitude()
                ),
                new ScheduleResponse.DayLocation(
                        day.getEndPlaceName(),
                        day.getEndLongitude(),
                        day.getEndLatitude()
                ),
                daySummary(schedule, day),
                stops,
                finalTransit
        );
    }

    private Map<UUID, TransitRoute> inboundRouteByStopId(ScheduleDay day) {
        return day.getTransitRoutes().stream()
                .filter(route -> route.getScheduleStop() != null)
                .collect(Collectors.toMap(
                        route -> route.getScheduleStop().getId(),
                        Function.identity()
                ));
    }

    private ScheduleResponse.Stop toStopResponse(
            ScheduleStop stop,
            LocalTime arriveAt,
            LocalTime departAt,
            ScheduleResponse.Transit inboundTransit
    ) {
        Place place = stop.getPlace();
        return new ScheduleResponse.Stop(
                stop.getId(),
                stop.getStopOrder(),
                arriveAt,
                departAt,
                stop.getStayMinutes(),
                new ScheduleResponse.Place(
                        place.getId(),
                        place.getName(),
                        place.getCategory(),
                        place.getAddress(),
                        place.getLongitude(),
                        place.getLatitude(),
                        place.getPrimaryImageUrl(),
                        operatingInfo(place)
                ),
                inboundTransit,
                jsonArrayValues(stop.getSelectionReasonsJson()),
                jsonArrayValues(stop.getWarningsJson())
        );
    }

    private ScheduleResponse.OperatingInfo operatingInfo(Place place) {
        if (place.getOperatingInfo() == null) {
            return null;
        }
        return new ScheduleResponse.OperatingInfo(
                place.getOperatingInfo().getOpeningHoursText(),
                place.getOperatingInfo().getClosedDaysText(),
                place.getOperatingInfo().isRequiresManualCheck()
        );
    }

    private ScheduleResponse.Transit toTransitResponse(
            Schedule schedule,
            ScheduleDay day,
            TransitRoute route,
            LocalTime departAt
    ) {
        LocalTime arriveAt = departAt.plusMinutes(route.getTotalMinutes());
        return new ScheduleResponse.Transit(
                route.getRouteType(),
                route.getRouteOrder(),
                routeOriginName(schedule, day, route),
                routeDestinationName(day, route),
                routeSummary(route),
                departAt,
                arriveAt,
                route.getTotalMinutes(),
                walkMinutes(route),
                waitMinutes(route),
                transferCount(route),
                route.getFareAmount(),
                route.getProvider(),
                route.getRealtimeStatus(),
                route.isFallbackUsed(),
                route.getSegments()
                        .stream()
                        .map(segment -> toSegmentResponse(route, segment))
                        .toList(),
                routeWarnings(route)
        );
    }

    private ScheduleResponse.Segment toSegmentResponse(TransitRoute route, TransitSegment segment) {
        int durationMinutes = segmentDuration(route, segment);
        return new ScheduleResponse.Segment(
                segment.getSegmentOrder(),
                segment.getMode(),
                segment.getLineName(),
                segment.getStartStationId(),
                segment.getStartStationName(),
                segment.getEndStationId(),
                segment.getEndStationName(),
                segment.getInstruction(),
                durationMinutes,
                segment.getDistanceMeters(),
                segment.getStationCount(),
                segment.getWaitMinutes(),
                segment.getRealtimeStatus()
        );
    }

    private ScheduleMapResponse.StopMarker toStopMarker(ScheduleDay day, ScheduleStop stop, StopTime stopTime) {
        Place place = stop.getPlace();
        List<String> warnings = jsonArrayValues(stop.getWarningsJson());
        return new ScheduleMapResponse.StopMarker(
                day.getDayNo(),
                stop.getStopOrder(),
                place.getId(),
                place.getName(),
                stopTime == null ? null : stopTime.arriveAt(),
                stopTime == null ? null : stopTime.departAt(),
                markerSubtitle(place, stop),
                warnings.isEmpty() ? "NORMAL" : "NOTICE",
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
                    routeLine.getDurationMinutes(),
                    routeLine.getDistanceMeters(),
                    routeLine.getInstruction(),
                    routeLine.isFallbackUsed(),
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
            return firstNonBlank(segment.getEndStationName(), routeDestinationName(day, route));
        }

        TransitSegment nextTransit = nextTransitSegment(route, segmentOrder);
        if (nextTransit != null) {
            return firstNonBlank(nextTransit.getStartStationName(), routeDestinationName(day, route));
        }
        return routeDestinationName(day, route);
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
            return day.getStartPlaceName();
        }
        int previousStopOrder = route.getRouteOrder() - 1;
        return day.getStops()
                .stream()
                .filter(stop -> stop.getStopOrder() == previousStopOrder)
                .findFirst()
                .map(stop -> stop.getPlace().getName())
                .orElse(day.getStartPlaceName());
    }

    private String routeDestinationName(ScheduleDay day, TransitRoute route) {
        if ("FINAL".equals(route.getRouteType()) || route.getScheduleStop() == null) {
            return day.getEndPlaceName();
        }
        return route.getScheduleStop().getPlace().getName();
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private List<String> selectionReasons(Place place, boolean autoGenerated, ScheduleCreateRequest request) {
        List<String> reasons = new ArrayList<>();
        if (!autoGenerated) {
            reasons.add("사용자가 반드시 방문할 장소로 선택했습니다.");
        } else {
            reasons.add("출발지와 도착지 기준 동선 점수가 높은 장소입니다.");
        }
        answerId(request, "THEME").ifPresent(answerId -> {
            if (placePreferenceScorer.themeScore(place, request) < 0) {
                reasons.add(themeReason(answerId));
            }
        });
        if (hasAnswer(request, "COMPANION_PARENTS") || hasAnswer(request, "COMPANION_FAMILY_WITH_CHILD")) {
            reasons.add("동행 조건을 고려해 무리한 이동을 줄이는 방향으로 배치했습니다.");
        }
        if (hasAnswer(request, "TRANSIT_SIMPLE")) {
            reasons.add("환승을 줄이는 선호 조건을 반영했습니다.");
        }
        return reasons.stream()
                .filter(reason -> reason != null && !reason.isBlank())
                .distinct()
                .toList();
    }

    private String themeReason(String answerId) {
        return switch (answerId) {
            case "THEME_FOOD" -> "맛집 테마와 일치하는 장소입니다.";
            case "THEME_HISTORY_CULTURE" -> "역사·문화 테마와 일치하는 장소입니다.";
            case "THEME_NATURE" -> "바다·자연 테마와 일치하는 장소입니다.";
            case "THEME_NIGHT_VIEW" -> "야경 테마와 일치하는 장소입니다.";
            case "THEME_EVENT" -> "축제·행사 테마와 일치하는 장소입니다.";
            case "THEME_LOCAL" -> "로컬 테마와 일치하는 장소입니다.";
            default -> null;
        };
    }

    private List<String> stopWarnings(Place place, ScheduleCreateRequest request) {
        List<String> warnings = new ArrayList<>();
        if (place.getOperatingInfo() != null && place.getOperatingInfo().isRequiresManualCheck()) {
            warnings.add("운영시간 원문 확인이 필요한 장소입니다.");
        }
        if (placePreferenceScorer.mobilityPenalty(place, request) > 0) {
            warnings.add("언덕·계단 부담이 있을 수 있어 현장 이동 동선을 확인하세요.");
        }
        return warnings;
    }

    private String instruction(TransitRouteResult.Segment segment) {
        if (segment.instruction() != null && !segment.instruction().isBlank()) {
            return segment.instruction();
        }
        String startName = firstNonBlank(segment.startStationName(), "출발지");
        String endName = firstNonBlank(segment.endStationName(), "도착지");
        if ("WALK".equals(segment.mode())) {
            return startName + "에서 " + endName + "까지 도보 이동";
        }
        String lineName = firstNonBlank(segment.lineName(), segment.mode());
        return startName + "에서 " + lineName + " 승차 후 " + endName + "에서 하차";
    }

    private int segmentDuration(TransitRouteResult.Segment segment, TransitRouteResult result) {
        if (segment.durationMinutes() > 0) {
            return segment.durationMinutes();
        }
        if (result.segments().size() == 1) {
            return result.totalMinutes();
        }
        return 0;
    }

    private int segmentDuration(TransitRoute route, TransitSegment segment) {
        if (segment.getDurationMinutes() > 0) {
            return segment.getDurationMinutes();
        }
        if (route.getSegments().size() == 1) {
            return route.getTotalMinutes();
        }
        return 0;
    }

    private String daySummary(Schedule schedule, ScheduleDay day) {
        String stopNames = day.getStops()
                .stream()
                .map(stop -> stop.getPlace().getName())
                .collect(Collectors.joining(" → "));
        if (stopNames.isBlank()) {
            return day.getStartPlaceName() + "에서 출발하는 빈 일정입니다.";
        }
        return day.getStartPlaceName() + " 출발 → " + stopNames + " → " + day.getEndPlaceName() + " 도착";
    }

    private String routeSummary(TransitRoute route) {
        List<String> labels = route.getSegments()
                .stream()
                .filter(segment -> !"WALK".equals(segment.getMode()))
                .map(segment -> firstNonBlank(segment.getLineName(), segment.getMode()))
                .distinct()
                .toList();
        if (labels.isEmpty()) {
            return "도보 이동";
        }
        return String.join(" + ", labels);
    }

    private int walkMinutes(TransitRoute route) {
        return route.getSegments()
                .stream()
                .filter(segment -> "WALK".equals(segment.getMode()))
                .mapToInt(segment -> segmentDuration(route, segment))
                .sum();
    }

    private int waitMinutes(TransitRoute route) {
        return route.getSegments()
                .stream()
                .mapToInt(TransitSegment::getWaitMinutes)
                .sum();
    }

    private int transferCount(TransitRoute route) {
        long transitSegments = route.getSegments()
                .stream()
                .filter(segment -> !"WALK".equals(segment.getMode()))
                .count();
        return (int) Math.max(0, transitSegments - 1);
    }

    private List<String> routeWarnings(TransitRoute route) {
        List<String> warnings = new ArrayList<>(jsonArrayValues(route.getWarningsJson()));
        if (route.isFallbackUsed()) {
            warnings.add("외부 경로 API 실패로 fallback 경로를 사용했습니다.");
        }
        if (route.getTotalMinutes() >= 45) {
            warnings.add("이동시간이 긴 구간입니다.");
        }
        return warnings.stream().distinct().toList();
    }

    private String markerSubtitle(Place place, ScheduleStop stop) {
        String category = firstNonBlank(place.getCategory(), "장소");
        return category + " · 체류 " + stop.getStayMinutes() + "분";
    }

    private Map<UUID, StopTime> stopTimes(Schedule schedule, ScheduleDay day) {
        Map<UUID, StopTime> times = new HashMap<>();
        Map<UUID, TransitRoute> inboundRouteByStopId = inboundRouteByStopId(day);
        LocalTime cursor = day.getStartTime();
        for (ScheduleStop stop : day.getStops()) {
            TransitRoute inboundRoute = inboundRouteByStopId.get(stop.getId());
            if (inboundRoute != null) {
                cursor = cursor.plusMinutes(inboundRoute.getTotalMinutes());
            }
            LocalTime arriveAt = cursor;
            LocalTime departAt = arriveAt.plusMinutes(stop.getStayMinutes());
            times.put(stop.getId(), new StopTime(arriveAt, departAt));
            cursor = departAt;
        }
        return times;
    }

    private String jsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return "[" + values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> "\"" + escapeJson(value) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private List<String> jsonArrayValues(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        while (matcher.find()) {
            values.add(unescapeJson(matcher.group(1)));
        }
        return values;
    }

    private String unescapeJson(String value) {
        return value
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static final class PlannerExecutionMetrics {

        private int routeResolutionCount;
        private int routeCacheHitCount;
        private int providerCallCount;
        private int providerFailureCount;
    }

    private enum RouteResolutionMode {
        ESTIMATE,
        DETAILED
    }

    private record RouteKey(TransitPoint origin, TransitPoint destination) {
    }

    private record StopTime(LocalTime arriveAt, LocalTime departAt) {
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
