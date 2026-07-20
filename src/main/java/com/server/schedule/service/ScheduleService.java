package com.server.schedule.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.metrics.ExternalCallMetricsCollector;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.place.support.PlaceCategoryLabelResolver;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.SchedulePreview;
import com.server.schedule.domain.TransitRoute;
import com.server.schedule.domain.TransitRouteLine;
import com.server.schedule.domain.TransitSegment;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleEvaluationReport;
import com.server.schedule.dto.ScheduleListResponse;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.evaluation.ScheduleHardGateEvaluator;
import com.server.schedule.evaluation.ScheduleHardGateResult;
import com.server.schedule.evaluation.ScheduleScoreEvaluator;
import com.server.schedule.evaluation.ScheduleScoreResult;
import com.server.schedule.planner.DayPlaceAllocator;
import com.server.schedule.planner.DayRouteOptimizer;
import com.server.schedule.planner.DailyScheduleTargetPolicy;
import com.server.schedule.planner.PlaceCountPolicy;
import com.server.schedule.planner.ScheduleFeasibilityChecker;
import com.server.schedule.planner.FixedEventPlanner;
import com.server.schedule.planner.PlacePreferenceScorer;
import com.server.schedule.planner.PlaceCandidateProvider;
import com.server.schedule.planner.MultiDayPlanOptimizer;
import com.server.schedule.planner.MealTimePolicy;
import com.server.schedule.planner.PlannerRouteEstimator;
import com.server.schedule.planner.PlanObjective;
import com.server.schedule.planner.PlanObjectiveEvaluator;
import com.server.schedule.planner.SchedulePlannerProperties;
import com.server.schedule.planner.ScheduleRepairContext;
import com.server.schedule.planner.ScheduleRepairEngine;
import com.server.schedule.planner.ScheduleRepairStrategy;
import com.server.schedule.planner.RepairCandidate;
import com.server.schedule.planner.AiSchedulePlanGenerator;
import com.server.schedule.planner.VisitDurationPolicy;
import com.server.schedule.repository.ScheduleRepository;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteProvider;
import com.server.transit.service.TransitRouteEstimate;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

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
    private final FixedEventPlanner fixedEventPlanner;
    private final PlannerRouteEstimator plannerRouteEstimator;
    private final SchedulePlannerProperties plannerProperties;
    private final AiSchedulePlanGenerator aiSchedulePlanGenerator;
    private final ScheduleRepairEngine scheduleRepairEngine;

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
            ExternalCallMetricsCollector externalCallMetricsCollector,
            FixedEventPlanner fixedEventPlanner,
            PlannerRouteEstimator plannerRouteEstimator,
            SchedulePlannerProperties plannerProperties,
            AiSchedulePlanGenerator aiSchedulePlanGenerator,
            ScheduleRepairEngine scheduleRepairEngine
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
        this.fixedEventPlanner = fixedEventPlanner;
        this.plannerRouteEstimator = plannerRouteEstimator;
        this.plannerProperties = plannerProperties;
        this.aiSchedulePlanGenerator = aiSchedulePlanGenerator;
        this.scheduleRepairEngine = scheduleRepairEngine;
    }

    public ScheduleResponse create(ScheduleCreateRequest request) {
        return createInternal(request, null);
    }

    public ScheduleResponse createFromPreview(
            ScheduleCreateRequest request,
            SchedulePreview preview,
            List<SchedulePreviewResponse.ResolvedDay> resolvedDays,
            List<String> planningWarnings,
            List<SchedulePreviewCreateRequest.FixedEvent> fixedEvents,
            String customPrompt
    ) {
        return createInternal(request, new PreviewPlanningOptions(
                preview, resolvedDays, planningWarnings, fixedEvents, customPrompt));
    }

    private ScheduleResponse createInternal(
            ScheduleCreateRequest request,
            PreviewPlanningOptions planningOptions
    ) {
        try (ExternalCallMetricsCollector.Scope externalMetrics = externalCallMetricsCollector.start()) {
        long startedAt = System.nanoTime();
        PlannerExecutionMetrics executionMetrics = new PlannerExecutionMetrics();
        if (planningOptions == null) requestValidator.validate(request);
        int tripDays = tripDays(request);
        List<PlaceCountPolicy> dailyPlaceCountPolicies = planningOptions == null
                ? dailyPlaceCountPolicies(request, tripDays)
                : planningOptions.resolvedDays().stream()
                        .map(day -> placeCountPolicyForAvailableMinutes(
                                Duration.between(day.availableFrom(), day.availableUntil()).toMinutes(), request))
                        .toList();
        ScheduleCreateRequest.Location overallStart = planningOptions == null
                ? overallStartLocation(request)
                : toCreateLocation(planningOptions.resolvedDays().get(0).startLocation(), request.startLocation());
        ScheduleCreateRequest.Location overallEnd = planningOptions == null
                ? overallEndLocation(request, tripDays)
                : toCreateLocation(planningOptions.resolvedDays().get(tripDays - 1).endLocation(), null);
        Schedule schedule = new Schedule(
                request.startDate(),
                request.endDate(),
                request.dailyStartTime(),
                request.dailyEndTime(),
                overallStart.name(),
                overallStart.longitude(),
                overallStart.latitude(),
                overallEnd == null ? null : overallEnd.name(),
                overallEnd == null ? null : overallEnd.longitude(),
                overallEnd == null ? null : overallEnd.latitude(),
                styleSummary(request),
                conditionJson(request)
        );

        List<ScheduleDay> days = planningOptions == null
                ? createDays(schedule, request)
                : createResolvedDays(schedule, planningOptions.resolvedDays());
        List<SchedulePreviewCreateRequest.FixedEvent> fixedEvents = planningOptions == null
                ? List.of() : planningOptions.fixedEvents();
        dailyPlaceCountPolicies = policiesWithRequiredCapacity(
                dailyPlaceCountPolicies, days, request.mustVisitPlaceIdsOrEmpty().size(), fixedEvents);
        List<Integer> dailyStopTargets = targetCounts(dailyPlaceCountPolicies);
        PlaceCandidateProvider.ResolvedPlaces resolvedPlaces = placeCandidateProvider.resolve(
                request, dailyStopTargets, days);
        dailyPlaceCountPolicies = policiesForAvailableCandidates(
                dailyPlaceCountPolicies, days, resolvedPlaces.places().size(), fixedEvents);
        int reducedOptionalStops = createStopsAndRoutes(
                days, resolvedPlaces, dailyPlaceCountPolicies, request, executionMetrics,
                fixedEvents,
                planningOptions == null ? null : planningOptions.customPrompt());
        if (planningOptions != null) {
            List<String> warnings = new ArrayList<>(planningOptions.planningWarnings());
            if (reducedOptionalStops > 0) {
                warnings.add("OPTIONAL_STOPS_REDUCED_FOR_FEASIBILITY");
            }
            schedule.applyPreview(
                    planningOptions.preview(), planningOptions.preview().getTimeZone(),
                    planningOptions.preview().getLodgingMode(), planningOptions.preview().getRouteCoverage(),
                    jsonArray(warnings));
        }
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

    private ScheduleCreateRequest.Location toCreateLocation(
            SchedulePreviewResponse.Location location,
            ScheduleCreateRequest.Location fallback
    ) {
        if (location == null) return fallback;
        return new ScheduleCreateRequest.Location(location.name(), location.longitude(), location.latitude());
    }

    private List<ScheduleDay> createResolvedDays(
            Schedule schedule,
            List<SchedulePreviewResponse.ResolvedDay> resolvedDays
    ) {
        List<ScheduleDay> days = new ArrayList<>();
        for (int index = 0; index < resolvedDays.size(); index++) {
            SchedulePreviewResponse.ResolvedDay resolved = resolvedDays.get(index);
            SchedulePreviewResponse.Location start = resolved.startLocation();
            SchedulePreviewResponse.Location end = resolved.endLocation();
            days.add(new ScheduleDay(
                    schedule, index + 1, resolved.date(), resolved.availableFrom(), resolved.availableUntil(),
                    start == null ? null : start.name(), start == null ? null : start.longitude(),
                    start == null ? null : start.latitude(), end == null ? null : end.name(),
                    end == null ? null : end.longitude(), end == null ? null : end.latitude(),
                    resolved.startLocationSource(), resolved.endLocationSource()));
        }
        return days;
    }

    @Transactional(readOnly = true)
    public ScheduleListResponse getAll() {
        return new ScheduleListResponse(scheduleRepository.findAllByOrderByStartDateAscCreatedAtDesc()
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
                firstDay.getStartLongitude() == null ? null : new ScheduleMapResponse.Marker(
                        firstDay.getStartPlaceName(), firstDay.getStartLongitude(), firstDay.getStartLatitude()),
                lastDay.getEndLongitude() == null ? null : new ScheduleMapResponse.Marker(
                        lastDay.getEndPlaceName(), lastDay.getEndLongitude(), lastDay.getEndLatitude()),
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
            if (orders == null || orders.isEmpty()
                    || orders.size() > DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY) {
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
        RouteSearchContext routeSearch = new RouteSearchContext();
        PlannerExecutionMetrics executionMetrics = new PlannerExecutionMetrics();
        for (ScheduleDay day : schedule.getDays()) {
            resolvePlannerEndpoints(day, day.getStops().stream()
                    .map(ScheduleStop::getPlace)
                    .toList());
            TransitPoint previous = day.getStartLongitude() == null ? null : new TransitPoint(
                    day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
            for (ScheduleStop stop : day.getStops()) {
                TransitPoint destination = new TransitPoint(
                        stop.getPlace().getName(),
                        stop.getPlace().getLongitude(),
                        stop.getPlace().getLatitude()
                );
                if (previous != null && !samePoint(previous, destination)) {
                    TransitRouteResult route = resolveRoute(
                            routeSearch, executionMetrics, previous, destination);
                    createRoute(day, stop, "INBOUND", stop.getStopOrder(), route);
                }
                previous = destination;
            }
            if (day.getEndLongitude() != null && previous != null
                    && !samePoint(previous, new TransitPoint(
                            day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude()))) {
                TransitRouteResult finalRoute = resolveRoute(
                        routeSearch, executionMetrics, previous,
                        new TransitPoint(day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude()));
                createRoute(day, null, "FINAL", day.getStops().size() + 1, finalRoute);
            }
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

    private List<PlaceCountPolicy> dailyPlaceCountPolicies(ScheduleCreateRequest request, int tripDays) {
        Map<Integer, ScheduleCreateRequest.DayCondition> dayConditions = request.daysOrEmpty()
                .stream()
                .collect(Collectors.toMap(ScheduleCreateRequest.DayCondition::dayNo, Function.identity()));
        return IntStream.rangeClosed(1, tripDays)
                .mapToObj(dayNo -> {
                    ScheduleCreateRequest.DayCondition condition = dayConditions.get(dayNo);
                    LocalTime startTime = condition == null ? request.dailyStartTime() : condition.startTime();
                    LocalTime endTime = condition == null ? request.dailyEndTime() : condition.endTime();
                    return placeCountPolicyForAvailableMinutes(
                            Duration.between(startTime, endTime).toMinutes(), request);
                })
                .toList();
    }

    private PlaceCountPolicy placeCountPolicyForAvailableMinutes(
            long availableMinutes,
            ScheduleCreateRequest request
    ) {
        return DailyScheduleTargetPolicy.policy(availableMinutes, request.selectedAnswers());
    }

    private List<PlaceCountPolicy> policiesWithRequiredCapacity(
            List<PlaceCountPolicy> policies,
            List<ScheduleDay> days,
            int requiredPlaceCount,
            List<SchedulePreviewCreateRequest.FixedEvent> fixedEvents
    ) {
        List<PlaceCountPolicy> adjusted = new ArrayList<>(policies);
        for (int index = 0; index < days.size(); index++) {
            LocalDate date = days.get(index).getDate();
            int fixedCount = (int) fixedEvents.stream()
                    .filter(event -> java.time.OffsetDateTime.parse(event.startsAt())
                            .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                            .toLocalDate().equals(date))
                    .count();
            adjusted.set(index, adjusted.get(index).withRequiredCount(fixedCount));
        }
        int maximumCapacity = adjusted.stream().mapToInt(PlaceCountPolicy::maximum).sum();
        if (requiredPlaceCount > maximumCapacity) {
            throw new BusinessException(ErrorCode.MUST_VISIT_PLACE_LIMIT_EXCEEDED);
        }
        return List.copyOf(adjusted);
    }

    private List<Integer> targetCounts(List<PlaceCountPolicy> policies) {
        return policies.stream().map(PlaceCountPolicy::targetCount).toList();
    }

    private List<PlaceCountPolicy> policiesForAvailableCandidates(
            List<PlaceCountPolicy> policies,
            List<ScheduleDay> days,
            int candidateCount,
            List<SchedulePreviewCreateRequest.FixedEvent> fixedEvents
    ) {
        List<Integer> adjustedTargets = new ArrayList<>(targetCounts(policies));
        int targetCount = adjustedTargets.stream().mapToInt(Integer::intValue).sum();
        List<Integer> minimumTargets = new ArrayList<>();
        for (ScheduleDay day : days) {
            int fixedCount = (int) fixedEvents.stream()
                    .filter(event -> java.time.OffsetDateTime.parse(event.startsAt())
                            .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                            .toLocalDate().equals(day.getDate()))
                    .count();
            minimumTargets.add(Math.max(fixedCount, candidateCount >= days.size() ? 1 : 0));
        }
        while (targetCount > candidateCount) {
            int index = -1;
            for (int candidateIndex = 0; candidateIndex < adjustedTargets.size(); candidateIndex++) {
                if (adjustedTargets.get(candidateIndex) > minimumTargets.get(candidateIndex)
                        && (index < 0 || adjustedTargets.get(candidateIndex) > adjustedTargets.get(index))) {
                    index = candidateIndex;
                }
            }
            if (index < 0) break;
            adjustedTargets.set(index, adjustedTargets.get(index) - 1);
            targetCount--;
        }
        List<PlaceCountPolicy> adjusted = new ArrayList<>();
        for (int index = 0; index < policies.size(); index++) {
            int target = adjustedTargets.get(index);
            if (target == 0 || target == policies.get(index).targetCount()) {
                adjusted.add(policies.get(index));
            } else {
                adjusted.add(policies.get(index).limitToAvailableCandidates(target));
            }
        }
        return List.copyOf(adjusted);
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

    private int createStopsAndRoutes(
            List<ScheduleDay> days,
            PlaceCandidateProvider.ResolvedPlaces resolvedPlaces,
            List<PlaceCountPolicy> dailyPlaceCountPolicies,
            ScheduleCreateRequest request,
            PlannerExecutionMetrics executionMetrics,
            List<SchedulePreviewCreateRequest.FixedEvent> fixedEvents,
            String customPrompt
    ) {
        List<Integer> dailyStopTargets = targetCounts(dailyPlaceCountPolicies);
        List<Place> places = resolvedPlaces.places();
        Map<LocalDate, Set<Long>> fixedPlaceIdsByDate = fixedEvents.stream().collect(Collectors.groupingBy(
                event -> java.time.OffsetDateTime.parse(event.startsAt())
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalDate(),
                Collectors.mapping(SchedulePreviewCreateRequest.FixedEvent::placeId, Collectors.toSet())));
        Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId = fixedEvents.stream()
                .collect(Collectors.toMap(SchedulePreviewCreateRequest.FixedEvent::placeId, Function.identity()));

        CompletableFuture<AiSchedulePlanGenerator.Result> aiProposalFuture = CompletableFuture.supplyAsync(
                () -> aiSchedulePlanGenerator.generate(
                        places, resolvedPlaces.mustVisitPlaceIds(), days, dailyStopTargets,
                        request, fixedPlaceIdsByDate, customPrompt));
        List<MultiDayPlanOptimizer.OptimizedPlan> deterministicCandidates = multiDayPlanOptimizer.rankedWithPolicies(
                places,
                resolvedPlaces.mustVisitPlaceIds(),
                days,
                dailyPlaceCountPolicies,
                request,
                plannerProperties.multiDayActualRerankCandidates()
        );
        AiSchedulePlanGenerator.Result aiProposal = aiProposalFuture.join();
        List<MultiDayPlanOptimizer.OptimizedPlan> planCandidates = mergeAiProposal(
                deterministicCandidates, aiProposal,
                plannerProperties.multiDayActualRerankCandidates());
        executionMetrics.multiDayPlanCandidateCount = planCandidates.size();
        executionMetrics.aiPlanConfidence = aiProposal.confidence();

        RouteSearchContext routeSearch = new RouteSearchContext();
        List<List<Place>> placesByDay = selectMultiDayPlan(
                planCandidates, days, request, routeSearch, executionMetrics, fixedEvents.isEmpty());
        executionMetrics.planningMode = aiProposal.hasProposal()
                ? (multiDayAssignmentKey(placesByDay).equals(
                        multiDayAssignmentKey(aiProposal.placesByDay()))
                        ? "AI_GENERATED" : "AI_ASSISTED")
                : aiProposal.source();
        placesByDay = fixedEventPlanner.placeOnRequiredDays(
                placesByDay, days, dailyPlaceCountPolicies.stream()
                        .map(PlaceCountPolicy::maximum).toList(), fixedPlaceIdsByDate);
        return repairAndCreateStopsAndRoutes(
                days, placesByDay, resolvedPlaces, dailyPlaceCountPolicies, request,
                routeSearch, executionMetrics, fixedEventByPlaceId);
    }

    private int repairAndCreateStopsAndRoutes(
            List<ScheduleDay> days,
            List<List<Place>> initialPlacesByDay,
            PlaceCandidateProvider.ResolvedPlaces resolvedPlaces,
            List<PlaceCountPolicy> dailyPlaceCountPolicies,
            ScheduleCreateRequest request,
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics,
            Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId
    ) {
        List<List<Place>> placesByDay = initialPlacesByDay;
        Map<Integer, List<Place>> orderOverrides = Map.of();
        int reducedOptionalStops = 0;
        int maximumRepairAttempts = 8;
        for (int attempt = 0; attempt < maximumRepairAttempts; attempt++) {
            List<List<Place>> orders = rebuildDays(
                    days, placesByDay, orderOverrides, resolvedPlaces, fixedEventByPlaceId,
                    request, routeSearch, executionMetrics);
            int failedDayIndex = firstInfeasibleDay(days);
            if (failedDayIndex < 0) {
                fixedEventPlanner.validateDetailedFeasibility(days);
                return reducedOptionalStops;
            }
            ScheduleRepairContext context = new ScheduleRepairContext(
                    failedDayIndex, placesByDay, resolvedPlaces.places(), orders.get(failedDayIndex),
                    days, dailyPlaceCountPolicies, resolvedPlaces.mustVisitPlaceIds(),
                    fixedEventByPlaceId.keySet(), request, placePreferenceScorer);
            RepairCandidate accepted = null;
            for (ScheduleRepairStrategy strategy : scheduleRepairEngine.strategies()) {
                rebuildDays(days, placesByDay, orderOverrides, resolvedPlaces,
                        fixedEventByPlaceId, request, routeSearch, executionMetrics);
                accepted = acceptedRepairCandidate(
                        strategy, context, days, resolvedPlaces, fixedEventByPlaceId,
                        request, routeSearch, executionMetrics);
                if (accepted != null) break;
            }
            if (accepted == null) {
                ScheduleDay failedDay = days.get(failedDayIndex);
                throw new BusinessException("END_CONSTRAINT".equals(failedDay.getEndLocationSource())
                        ? ErrorCode.END_CONSTRAINT_UNREACHABLE : ErrorCode.INVALID_SCHEDULE_CONDITION);
            }
            if (accepted.type() == RepairCandidate.RepairType.LOW_UTILITY_REMOVAL) {
                reducedOptionalStops++;
            }
            if (accepted.reduceToMinimumStay()) {
                fixedEventPlanner.validateDetailedFeasibility(days);
                return reducedOptionalStops;
            }
            placesByDay = accepted.placesByDay();
            orderOverrides = accepted.orderOverrides();
        }
        int failedDayIndex = firstInfeasibleDay(days);
        if (failedDayIndex < 0) {
            fixedEventPlanner.validateDetailedFeasibility(days);
            return reducedOptionalStops;
        }
        ScheduleDay failedDay = days.get(failedDayIndex);
        throw new BusinessException("END_CONSTRAINT".equals(failedDay.getEndLocationSource())
                ? ErrorCode.END_CONSTRAINT_UNREACHABLE : ErrorCode.INVALID_SCHEDULE_CONDITION);
    }

    private RepairCandidate acceptedRepairCandidate(
            ScheduleRepairStrategy strategy,
            ScheduleRepairContext context,
            List<ScheduleDay> days,
            PlaceCandidateProvider.ResolvedPlaces resolvedPlaces,
            Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId,
            ScheduleCreateRequest request,
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics
    ) {
        PlanObjective currentObjective = repairObjective(days, context.placeCountPolicies());
        RepairCandidate bestImprovement = null;
        PlanObjective bestObjective = currentObjective;
        for (RepairCandidate candidate : strategy.repair(context)) {
            rebuildDays(days, candidate.placesByDay(), candidate.orderOverrides(), resolvedPlaces,
                    fixedEventByPlaceId, request, routeSearch, executionMetrics);
            boolean feasible = candidate.reduceToMinimumStay()
                    ? days.stream().allMatch(feasibilityChecker::fitWithinAvailableTime)
                    : firstInfeasibleDay(days) < 0;
            if (feasible) return candidate;
            PlanObjective candidateObjective = repairObjective(days, context.placeCountPolicies());
            if (!candidate.reduceToMinimumStay() && candidateObjective.compareTo(bestObjective) < 0) {
                bestImprovement = candidate;
                bestObjective = candidateObjective;
            }
        }
        return bestImprovement;
    }

    private List<List<Place>> rebuildDays(
            List<ScheduleDay> days,
            List<List<Place>> placesByDay,
            Map<Integer, List<Place>> orderOverrides,
            PlaceCandidateProvider.ResolvedPlaces resolvedPlaces,
            Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId,
            ScheduleCreateRequest request,
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics
    ) {
        List<List<Place>> orders = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            ScheduleDay day = days.get(dayIndex);
            day.clearTransitRoutes();
            day.clearStops();
            List<Place> orderedPlaces = orderOverrides.getOrDefault(dayIndex, List.of());
            if (orderedPlaces.isEmpty()) {
                orderedPlaces = optimizedOrder(
                        day, placesByDay.get(dayIndex), fixedEventByPlaceId, request,
                        routeSearch, executionMetrics, days.size(), dayIndex);
            }
            createDayStopsAndRoutes(
                    day, orderedPlaces, resolvedPlaces, fixedEventByPlaceId,
                    request, routeSearch, executionMetrics);
            resolvePlannerEndpoints(day, orderedPlaces);
            orders.add(List.copyOf(orderedPlaces));
        }
        return List.copyOf(orders);
    }

    private int firstInfeasibleDay(List<ScheduleDay> days) {
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            if (!feasibilityChecker.isWithinAvailableTime(days.get(dayIndex))) return dayIndex;
        }
        return -1;
    }

    private long totalOverrunMinutes(List<ScheduleDay> days) {
        return days.stream().mapToLong(day -> Math.max(0,
                feasibilityChecker.plannedMinutes(day)
                        - Duration.between(day.getStartTime(), day.getEndTime()).toMinutes())).sum();
    }

    private PlanObjective repairObjective(
            List<ScheduleDay> days,
            List<PlaceCountPolicy> placeCountPolicies
    ) {
        long routeFlowCost = 0;
        long placeCountCost = 0;
        for (int index = 0; index < days.size(); index++) {
            ScheduleDay day = days.get(index);
            routeFlowCost += DayRouteOptimizer.routeFlow(day, day.getStops().stream()
                    .map(ScheduleStop::getPlace).toList()).totalPenalty();
            placeCountCost += placeCountPolicies.get(index).placeCountCost(day.getStops().size());
        }
        return PlanObjectiveEvaluator.evaluate(
                0,
                totalOverrunMinutes(days),
                0,
                routeFlowCost,
                0,
                0,
                0,
                placeCountCost
        );
    }

    private List<MultiDayPlanOptimizer.OptimizedPlan> mergeAiProposal(
            List<MultiDayPlanOptimizer.OptimizedPlan> deterministicCandidates,
            AiSchedulePlanGenerator.Result aiProposal,
            int limit
    ) {
        if (!aiProposal.hasProposal()) return deterministicCandidates;
        List<MultiDayPlanOptimizer.OptimizedPlan> merged = new ArrayList<>();
        long neutralEstimatedCost = deterministicCandidates.get(0).estimatedCost();
        merged.add(new MultiDayPlanOptimizer.OptimizedPlan(
                neutralEstimatedCost, aiProposal.placesByDay()));
        Set<String> assignments = new LinkedHashSet<>();
        assignments.add(multiDayAssignmentKey(aiProposal.placesByDay()));
        for (MultiDayPlanOptimizer.OptimizedPlan candidate : deterministicCandidates) {
            if (assignments.add(multiDayAssignmentKey(candidate.placesByDay()))) {
                merged.add(candidate);
            }
            if (merged.size() >= limit) break;
        }
        return List.copyOf(merged);
    }

    private List<List<Place>> selectMultiDayPlan(
            List<MultiDayPlanOptimizer.OptimizedPlan> planCandidates,
            List<ScheduleDay> days,
            ScheduleCreateRequest request,
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics,
            boolean canRerank
    ) {
        MultiDayPlanOptimizer.OptimizedPlan first = planCandidates.get(0);
        if (!plannerProperties.actualRouteRerankEnabled()
                || !canRerank
                || days.size() < 2
                || planCandidates.size() < 2) {
            return first.placesByDay();
        }

        int remainingBudget = plannerProperties.maxRouteEstimateProviderCalls()
                - routeSearch.providerEstimateCount();
        if (remainingBudget <= 0) return first.placesByDay();

        Set<RouteKey> requiredProviderRoutes = new LinkedHashSet<>();
        List<MultiDayPlanOrder> accepted = new ArrayList<>();
        for (MultiDayPlanOptimizer.OptimizedPlan candidate : planCandidates) {
            List<List<Place>> orders = providerFreeOrders(days, candidate.placesByDay(), request);
            Set<RouteKey> nextRoutes = new LinkedHashSet<>(requiredProviderRoutes);
            for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
                nextRoutes.addAll(multiDayBoundaryRouteKeys(
                        days.get(dayIndex), orders.get(dayIndex), routeSearch));
            }
            if (nextRoutes.size() > remainingBudget) continue;
            requiredProviderRoutes = nextRoutes;
            accepted.add(new MultiDayPlanOrder(candidate, orders));
        }
        if (accepted.size() < 2) return first.placesByDay();

        MultiDayPlanResult best = null;
        for (MultiDayPlanOrder candidate : accepted) {
            try {
                long actualCost = 0;
                for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
                    ScheduleDay candidateDay = days.get(dayIndex);
                    actualCost += dayRouteOptimizer.bestOf(
                            candidateDay,
                            List.of(candidate.orders().get(dayIndex)),
                            (origin, destination) -> multiDayAssignmentRoute(
                                    candidateDay, routeSearch, executionMetrics,
                                    origin, destination),
                            optimizationPreference(request),
                            this::stayMinutes
                    ).optimizationCost();
                }
                executionMetrics.multiDayPlanRerankedCount++;
                MultiDayPlanResult evaluated = new MultiDayPlanResult(
                        candidate.plan(), candidate.orders(), actualCost);
                if (best == null || compareMultiDayPlans(evaluated, best) < 0) {
                    best = evaluated;
                }
            } catch (BusinessException ignored) {
                // A failed candidate is discarded; the deterministic first plan remains the fallback.
            }
        }
        return best == null ? first.placesByDay() : best.orders();
    }

    private Set<RouteKey> multiDayBoundaryRouteKeys(
            ScheduleDay day,
            List<Place> order,
            RouteSearchContext routeSearch
    ) {
        if (order.isEmpty()) return Set.of();
        Set<RouteKey> keys = new LinkedHashSet<>();
        TransitPoint first = new TransitPoint(
                order.get(0).getName(), order.get(0).getLongitude(), order.get(0).getLatitude());
        if (day.getStartLongitude() != null) {
            addProviderRouteKey(keys, routeSearch, new TransitPoint(
                    day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude()), first);
        }
        if (day.getEndLongitude() != null) {
            Place lastPlace = order.get(order.size() - 1);
            addProviderRouteKey(keys, routeSearch, new TransitPoint(
                    lastPlace.getName(), lastPlace.getLongitude(), lastPlace.getLatitude()),
                    new TransitPoint(
                            day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude()));
        }
        return Set.copyOf(keys);
    }

    private TransitRouteResult multiDayAssignmentRoute(
            ScheduleDay day,
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics,
            TransitPoint origin,
            TransitPoint destination
    ) {
        boolean startsAtDayBoundary = day.getStartLongitude() != null
                && samePoint(origin, day.getStartLongitude(), day.getStartLatitude());
        boolean endsAtDayBoundary = day.getEndLongitude() != null
                && samePoint(destination, day.getEndLongitude(), day.getEndLatitude());
        return startsAtDayBoundary || endsAtDayBoundary
                ? estimatedRoute(routeSearch, executionMetrics, origin, destination)
                : plannerRouteEstimator.estimate(origin, destination);
    }

    private boolean samePoint(TransitPoint point, BigDecimal longitude, BigDecimal latitude) {
        return point.longitude().compareTo(longitude) == 0
                && point.latitude().compareTo(latitude) == 0;
    }

    private List<List<Place>> providerFreeOrders(
            List<ScheduleDay> days,
            List<List<Place>> placesByDay,
            ScheduleCreateRequest request
    ) {
        List<List<Place>> orders = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            orders.add(dayRouteOptimizer.rankedWithMealPositionDiversity(
                    days.get(dayIndex),
                    placesByDay.get(dayIndex),
                    plannerRouteEstimator::estimate,
                    optimizationPreference(request),
                    this::stayMinutes,
                    1
            ).get(0).places());
        }
        return List.copyOf(orders);
    }

    private int compareMultiDayPlans(MultiDayPlanResult left, MultiDayPlanResult right) {
        int actual = Long.compare(left.actualCost(), right.actualCost());
        if (actual != 0) return actual;
        int estimated = Long.compare(left.plan().estimatedCost(), right.plan().estimatedCost());
        if (estimated != 0) return estimated;
        return multiDayPlanKey(left.orders()).compareTo(multiDayPlanKey(right.orders()));
    }

    private String multiDayPlanKey(List<List<Place>> placesByDay) {
        return placesByDay.stream()
                .map(day -> day.stream()
                        .map(place -> place.getId() == null ? place.getName() : place.getId().toString())
                        .collect(Collectors.joining(">")))
                .collect(Collectors.joining("|"));
    }

    private String multiDayAssignmentKey(List<List<Place>> placesByDay) {
        return placesByDay.stream()
                .map(day -> day.stream()
                        .map(place -> place.getId() == null ? place.getName() : place.getId().toString())
                        .sorted()
                        .collect(Collectors.joining(">")))
                .collect(Collectors.joining("|"));
    }

    private List<Place> optimizedOrder(
            ScheduleDay day,
            List<Place> places,
            Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId,
            ScheduleCreateRequest request,
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics,
            int tripDays,
            int dayIndex
    ) {
        DayRouteOptimizer.OptimizationPreference preference = optimizationPreference(request);
        List<DayRouteOptimizer.OptimizedDayRoute> localCandidates = dayRouteOptimizer
                .rankedWithMealPositionDiversity(
                day,
                places,
                plannerRouteEstimator::estimate,
                preference,
                this::stayMinutes,
                plannerProperties.actualRouteRerankCandidates());
        List<Place> routeOrder = localCandidates.get(0).places();
        boolean hasFixedEvent = places.stream()
                .anyMatch(place -> fixedEventByPlaceId.containsKey(place.getId()));
        if (plannerProperties.actualRouteRerankEnabled()
                && !hasFixedEvent
                && localCandidates.size() > 1) {
            List<List<Place>> rerankOrders = rerankOrdersWithinBudget(
                    day, localCandidates, routeSearch, tripDays, dayIndex);
            if (rerankOrders.size() > 1) {
                routeOrder = dayRouteOptimizer.bestOf(
                        day,
                        rerankOrders,
                        (origin, destination) -> estimatedRoute(
                                routeSearch, executionMetrics, origin, destination),
                        preference,
                        this::stayMinutes
                ).places();
            }
        }
        return fixedEventPlanner.optimizeOrder(
                day, routeOrder, fixedEventByPlaceId,
                plannerRouteEstimator::estimate, this::stayMinutes);
    }

    private List<List<Place>> rerankOrdersWithinBudget(
            ScheduleDay day,
            List<DayRouteOptimizer.OptimizedDayRoute> candidates,
            RouteSearchContext routeSearch,
            int tripDays,
            int dayIndex
    ) {
        int requestRemainingBudget = plannerProperties.maxRouteEstimateProviderCalls()
                - routeSearch.providerEstimateCount();
        int perDayBudget = (plannerProperties.maxRouteEstimateProviderCalls()
                + Math.max(1, tripDays) - 1) / Math.max(1, tripDays);
        int remainingDays = Math.max(1, tripDays - dayIndex);
        int fairRemainingBudget = requestRemainingBudget / remainingDays;
        int remainingBudget = Math.min(fairRemainingBudget, perDayBudget);
        if (remainingBudget <= 0) return List.of();

        Set<RouteKey> requiredProviderRoutes = new LinkedHashSet<>();
        List<List<Place>> accepted = new ArrayList<>();
        for (DayRouteOptimizer.OptimizedDayRoute candidate : candidates) {
            Set<RouteKey> nextRoutes = new LinkedHashSet<>(requiredProviderRoutes);
            nextRoutes.addAll(providerRouteKeys(day, candidate.places(), routeSearch));
            if (nextRoutes.size() > remainingBudget) break;
            requiredProviderRoutes = nextRoutes;
            accepted.add(candidate.places());
        }
        return List.copyOf(accepted);
    }

    private Set<RouteKey> providerRouteKeys(
            ScheduleDay day,
            List<Place> order,
            RouteSearchContext routeSearch
    ) {
        Set<RouteKey> keys = new LinkedHashSet<>();
        TransitPoint previous = day.getStartLongitude() == null ? null : new TransitPoint(
                day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
        for (Place place : order) {
            TransitPoint destination = new TransitPoint(
                    place.getName(), place.getLongitude(), place.getLatitude());
            addProviderRouteKey(keys, routeSearch, previous, destination);
            previous = destination;
        }
        if (previous != null && day.getEndLongitude() != null) {
            addProviderRouteKey(keys, routeSearch, previous, new TransitPoint(
                    day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude()));
        }
        return keys;
    }

    private void addProviderRouteKey(
            Set<RouteKey> keys,
            RouteSearchContext routeSearch,
            TransitPoint origin,
            TransitPoint destination
    ) {
        if (origin == null || destination == null) return;
        RouteKey key = new RouteKey(origin, destination);
        if (routeSearch.estimates.containsKey(key)
                || distanceMeters(origin.longitude(), origin.latitude(),
                        destination.longitude(), destination.latitude()) <= CLOSE_WALK_THRESHOLD_METERS) {
            return;
        }
        keys.add(key);
    }

    private void createDayStopsAndRoutes(
            ScheduleDay day,
            List<Place> orderedPlaces,
            PlaceCandidateProvider.ResolvedPlaces resolvedPlaces,
            Map<Long, SchedulePreviewCreateRequest.FixedEvent> fixedEventByPlaceId,
            ScheduleCreateRequest request,
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics
    ) {
        TransitPoint previous = day.getStartLongitude() == null ? null : new TransitPoint(
                day.getStartPlaceName(), day.getStartLongitude(), day.getStartLatitude());
        for (int stopIndex = 0; stopIndex < orderedPlaces.size(); stopIndex++) {
            Place place = orderedPlaces.get(stopIndex);
            TransitPoint destination = new TransitPoint(
                    place.getName(), place.getLongitude(), place.getLatitude());
            TransitRouteResult inboundRoute = previous == null ? null : selectedRoute(
                    routeSearch, executionMetrics, previous, destination);
            ScheduleStop stop = new ScheduleStop(day, place, stopIndex + 1, stayMinutes(place));
            SchedulePreviewCreateRequest.FixedEvent fixedEvent = fixedEventByPlaceId.get(place.getId());
            if (fixedEvent != null) stop.applyFixedEvent(
                    fixedEvent.clientEventId(), fixedEvent.name(),
                    java.time.OffsetDateTime.parse(fixedEvent.startsAt()),
                    java.time.OffsetDateTime.parse(fixedEvent.endsAt()));
            stop.updateDeliveryInfo(
                    jsonArray(selectionReasons(
                            place,
                            !resolvedPlaces.mustVisitPlaceIds().contains(place.getId()),
                            request
                    )),
                    jsonArray(stopWarnings(place, request))
            );
            if (inboundRoute != null) createRoute(day, stop, "INBOUND", stopIndex + 1, inboundRoute);
            previous = destination;
        }
        if (!orderedPlaces.isEmpty() && day.getEndLongitude() != null) {
            TransitRouteResult finalRoute = selectedRoute(
                    routeSearch,
                    executionMetrics,
                    previous,
                    new TransitPoint(day.getEndPlaceName(), day.getEndLongitude(), day.getEndLatitude())
            );
            createRoute(day, null, "FINAL", orderedPlaces.size() + 1, finalRoute);
        }
    }

    private Place optionalPlaceToRemove(
            ScheduleDay day,
            List<Place> orderedPlaces,
            Set<Long> mustVisitPlaceIds,
            Set<Long> fixedEventPlaceIds,
            ScheduleCreateRequest request,
            int absoluteMinimum
    ) {
        if (orderedPlaces.size() <= absoluteMinimum) return null;
        List<Place> optional = orderedPlaces.stream()
                .filter(place -> !mustVisitPlaceIds.contains(place.getId()))
                .filter(place -> !fixedEventPlaceIds.contains(place.getId()))
                .toList();
        if (optional.isEmpty()) return null;
        long nonMealCount = orderedPlaces.stream().filter(place -> !MealTimePolicy.isMealPlace(place)).count();
        List<Place> preferredRemoval = nonMealCount > 1
                ? optional.stream().filter(place -> !MealTimePolicy.isMealPlace(place)).toList()
                : optional.stream().filter(MealTimePolicy::isMealPlace).toList();
        if (preferredRemoval.isEmpty()) preferredRemoval = optional;
        ScheduleCreateRequest.Location start = locationForScore(day, true, request);
        ScheduleCreateRequest.Location end = locationForScore(day, false, request);
        return preferredRemoval.stream()
                .max(Comparator.comparingInt(place -> placePreferenceScorer
                        .score(place, start, end, request).totalScore()))
                .orElse(null);
    }

    private ScheduleCreateRequest.Location locationForScore(
            ScheduleDay day,
            boolean start,
            ScheduleCreateRequest request
    ) {
        BigDecimal longitude = start ? day.getStartLongitude() : day.getEndLongitude();
        BigDecimal latitude = start ? day.getStartLatitude() : day.getEndLatitude();
        String name = start ? day.getStartPlaceName() : day.getEndPlaceName();
        if (longitude != null && latitude != null) {
            return new ScheduleCreateRequest.Location(name, longitude, latitude);
        }
        if (!start && request.endLocation() != null) return request.endLocation();
        return request.startLocation();
    }

    private TransitRouteResult selectedRoute(
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics,
            TransitPoint origin,
            TransitPoint destination
    ) {
        return resolveRoute(routeSearch, executionMetrics, origin, destination);
    }

    private TransitRouteResult resolveRoute(
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics,
            TransitPoint origin,
            TransitPoint destination
    ) {
        executionMetrics.routeResolutionCount++;
        RouteKey key = new RouteKey(origin, destination);
        TransitRouteResult cached = routeSearch.details.get(key);
        if (cached != null) {
            executionMetrics.routeCacheHitCount++;
            return cached;
        }
        TransitRouteResult result = detailedRouteBetween(
                routeSearch, origin, destination, executionMetrics);
        routeSearch.details.put(key, result);
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
        return VisitDurationPolicy.minutes(place);
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

    private TransitRouteResult estimatedRoute(
            RouteSearchContext routeSearch,
            PlannerExecutionMetrics executionMetrics,
            TransitPoint origin,
            TransitPoint destination
    ) {
        executionMetrics.routeEstimateResolutionCount++;
        RouteKey key = new RouteKey(origin, destination);
        TransitRouteEstimate cached = routeSearch.estimates.get(key);
        if (cached != null) {
            executionMetrics.routeEstimateCacheHitCount++;
            return cached.route();
        }

        int distanceMeters = distanceMeters(
                origin.longitude(), origin.latitude(), destination.longitude(), destination.latitude());
        TransitRouteEstimate estimate;
        if (distanceMeters <= CLOSE_WALK_THRESHOLD_METERS) {
            estimate = TransitRouteEstimate.detailed(
                    walkRoute(origin, destination, distanceMeters, false));
        } else {
            try {
                routeSearch.providerEstimateCount++;
                executionMetrics.providerEstimateCallCount++;
                estimate = transitRouteProvider.findRouteEstimate(origin, destination);
            } catch (BusinessException exception) {
                executionMetrics.providerEstimateFailureCount++;
                if (isFallbackEligible(exception)
                        && distanceMeters <= PROVIDER_FAILURE_WALK_FALLBACK_METERS) {
                    estimate = TransitRouteEstimate.detailed(
                            walkRoute(origin, destination, distanceMeters, true));
                } else {
                    throw exception;
                }
            }
        }
        routeSearch.estimates.put(key, estimate);
        return estimate.route();
    }

    private TransitRouteResult detailedRouteBetween(
            RouteSearchContext routeSearch,
            TransitPoint origin,
            TransitPoint destination,
            PlannerExecutionMetrics executionMetrics
    ) {
        int distanceMeters = distanceMeters(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        );
        if (distanceMeters <= CLOSE_WALK_THRESHOLD_METERS) {
            return walkRoute(origin, destination, distanceMeters, false);
        }
        TransitRouteEstimate estimate = routeSearch.estimates.get(new RouteKey(origin, destination));
        if (estimate != null && !estimate.requiresDetail()) {
            return estimate.route();
        }
        try {
            executionMetrics.providerCallCount++;
            return estimate == null
                    ? transitRouteProvider.findRoute(origin, destination)
                    : transitRouteProvider.findRouteDetail(origin, destination, estimate);
        } catch (BusinessException exception) {
            executionMetrics.providerFailureCount++;
            if (isFallbackEligible(exception) && distanceMeters <= PROVIDER_FAILURE_WALK_FALLBACK_METERS) {
                return walkRoute(origin, destination, distanceMeters, true);
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
        boolean previewBased = schedule.getPreview() != null;
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getStatus(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                previewBased ? null : schedule.getDailyStartTime(),
                previewBased ? null : schedule.getDailyEndTime(),
                schedule.getStyleSummary(),
                schedule.getDays()
                        .stream()
                        .map(day -> toDayResponse(schedule, day))
                        .toList(),
                null,
                previewBased ? schedule.getPreview().getId() : null,
                previewBased ? new ScheduleResponse.PlanningAssumptions(
                        schedule.getTimeZone(), schedule.getLodgingMode(), schedule.getRouteCoverage(),
                        jsonArrayValues(schedule.getPlanningWarningsJson())) : null
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
                evaluation,
                response.previewId(),
                response.planningAssumptions()
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
                        evaluationCoveragePercent(scoreResult),
                        unusedMinutes(schedule),
                        longTransitWarnings(response),
                        routeConfidence(schedule, routes),
                        scoreResult.metrics().stream()
                                .map(metric -> new ScheduleEvaluationReport.Metric(
                                        metric.id(),
                                        metric.label(),
                                        metric.score(),
                                        metric.maxScore(),
                                        metric.reason(),
                                        metric.status()
                                ))
                                .toList()
                ),
                new ScheduleEvaluationReport.Operations(
                        generationMillis,
                        executionMetrics.planningMode,
                        executionMetrics.aiPlanConfidence,
                        executionMetrics.multiDayPlanCandidateCount,
                        executionMetrics.multiDayPlanRerankedCount,
                        executionMetrics.routeEstimateResolutionCount,
                        executionMetrics.routeEstimateCacheHitCount,
                        executionMetrics.providerEstimateCallCount,
                        executionMetrics.providerEstimateFailureCount,
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

    private int evaluationCoveragePercent(ScheduleScoreResult scoreResult) {
        int total = scoreResult.metrics().stream().mapToInt(ScheduleScoreResult.Metric::maxScore).sum();
        int evaluated = scoreResult.metrics().stream()
                .filter(metric -> "EVALUATED".equals(metric.status()))
                .mapToInt(ScheduleScoreResult.Metric::maxScore)
                .sum();
        return total == 0 ? 0 : (int) Math.round(evaluated * 100.0 / total);
    }

    private int unusedMinutes(Schedule schedule) {
        return schedule.getDays().stream()
                .mapToInt(day -> (int) Math.max(0,
                        Duration.between(day.getStartTime(), day.getEndTime()).toMinutes()
                                - feasibilityChecker.activeMinutes(day)))
                .sum();
    }

    private List<ScheduleEvaluationReport.LongTransitWarning> longTransitWarnings(
            ScheduleResponse response
    ) {
        return response.days().stream()
                .flatMap(day -> java.util.stream.Stream.concat(
                                day.stops().stream().map(ScheduleResponse.Stop::inboundTransit),
                                java.util.stream.Stream.of(day.finalTransit()))
                        .filter(Objects::nonNull)
                        .filter(route -> route.totalMinutes() > 60)
                        .map(route -> new ScheduleEvaluationReport.LongTransitWarning(
                                day.dayNo(), route.routeOrder(), route.originName(),
                                route.destinationName(), route.totalMinutes())))
                .toList();
    }

    private String routeConfidence(
            Schedule schedule,
            List<ScheduleResponse.Transit> routes
    ) {
        if (routes.isEmpty()) return "UNKNOWN";
        if (routes.stream().anyMatch(route -> "FAKE".equals(route.provider())
                || "UNKNOWN".equals(route.provider()))) {
            return "LOW";
        }
        boolean fallbackRoute = routes.stream().anyMatch(ScheduleResponse.Transit::fallbackUsed);
        boolean fallbackGeometry = schedule.getDays().stream()
                .flatMap(day -> day.getTransitRoutes().stream())
                .flatMap(route -> route.getRouteLines().stream())
                .anyMatch(TransitRouteLine::isFallbackUsed);
        return fallbackRoute || fallbackGeometry ? "MEDIUM" : "HIGH";
    }

    private ScheduleResponse.Day toDayResponse(Schedule schedule, ScheduleDay day) {
        List<ScheduleResponse.Stop> stops = new ArrayList<>();
        Map<UUID, TransitRoute> inboundRouteByStopId = inboundRouteByStopId(day);
        List<MealTimePolicy.MealSlot> mealSlots = MealTimePolicy.activeSlots(day);
        Set<MealTimePolicy.MealSlot> assignedMealSlots = EnumSet.noneOf(MealTimePolicy.MealSlot.class);
        LocalTime cursor = day.getStartTime();
        for (ScheduleStop stop : day.getStops()) {
            ScheduleResponse.Transit inboundTransit = null;
            TransitRoute inboundRoute = inboundRouteByStopId.get(stop.getId());
            if (inboundRoute != null) {
                inboundTransit = toTransitResponse(schedule, day, inboundRoute, cursor);
                cursor = cursor.plusMinutes(inboundRoute.getTotalMinutes());
            }
            LocalTime arriveAt = cursor;
            LocalTime departAt;
            String mealTimeSlot = null;
            int waitingMinutesBefore = 0;
            if (stop.getFixedStartsAt() != null) {
                LocalTime fixedStart = stop.getFixedStartsAt()
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalTime();
                arriveAt = arriveAt.isBefore(fixedStart) ? fixedStart : arriveAt;
                departAt = stop.getFixedEndsAt()
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalTime();
                MealTimePolicy.Alignment alignment = MealTimePolicy.alignArrival(
                        arriveAt, stop.getPlace(), mealSlots, assignedMealSlots);
                if (alignment.slot() != null) {
                    assignedMealSlots.add(alignment.slot());
                    mealTimeSlot = alignment.slot().name();
                }
            } else {
                MealTimePolicy.Alignment alignment = MealTimePolicy.alignArrival(
                        arriveAt, stop.getPlace(), mealSlots, assignedMealSlots);
                arriveAt = alignment.arrival();
                waitingMinutesBefore = alignment.waitingMinutes();
                if (alignment.slot() != null) {
                    assignedMealSlots.add(alignment.slot());
                    mealTimeSlot = alignment.slot().name();
                }
                departAt = arriveAt.plusMinutes(stop.getStayMinutes());
            }
            stops.add(toStopResponse(
                    stop, arriveAt, departAt, inboundTransit, mealTimeSlot, waitingMinutesBefore));
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
                day.getStartLongitude() == null ? null : new ScheduleResponse.DayLocation(
                        day.getStartPlaceName(),
                        day.getStartLongitude(),
                        day.getStartLatitude()
                ),
                day.getEndLongitude() == null ? null : new ScheduleResponse.DayLocation(
                        day.getEndPlaceName(),
                        day.getEndLongitude(),
                        day.getEndLatitude()
                ),
                day.getStartLocationSource(),
                day.getEndLocationSource(),
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
            ScheduleResponse.Transit inboundTransit,
            String mealTimeSlot,
            int waitingMinutesBefore
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
                        PlaceCategoryLabelResolver.resolve(place.getCategory(), place.getContentTypeId()),
                        place.getAddress(),
                        place.getLongitude(),
                        place.getLatitude(),
                        place.getPrimaryImageUrl(),
                        operatingInfo(place)
                ),
                inboundTransit,
                mealTimeSlot,
                waitingMinutesBefore,
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
            List<List<BigDecimal>> coordinates = coordinates(routeLine.getCoordinatesJson());
            response.add(new ScheduleMapResponse.RouteLine(
                    day.getDayNo(),
                    route.getRouteOrder(),
                    routeLine.getLineOrder(),
                    routeLine.getMode(),
                    routeLine.getLineName(),
                    routeLineStartName(schedule, day, route, segment, segmentOrder),
                    routeLineEndName(schedule, day, route, segment, segmentOrder),
                    routeLine.getDurationMinutes(),
                    routeLineDistanceMeters(routeLine.getDistanceMeters(), coordinates),
                    routeLine.getInstruction(),
                    routeLine.isFallbackUsed(),
                    coordinates
            ));
        }
        return response;
    }

    private int routeLineDistanceMeters(
            Integer providerDistanceMeters,
            List<List<BigDecimal>> coordinates
    ) {
        if (providerDistanceMeters != null && providerDistanceMeters > 0) {
            return providerDistanceMeters;
        }
        double total = 0;
        for (int index = 1; index < coordinates.size(); index++) {
            List<BigDecimal> previous = coordinates.get(index - 1);
            List<BigDecimal> current = coordinates.get(index);
            total += distanceMeters(previous.get(0), previous.get(1), current.get(0), current.get(1));
        }
        return Math.max(0, (int) Math.round(total));
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
        if (placePreferenceScorer.themeScore(place, request) < 0) {
            request.selectedAnswers().stream()
                    .filter(answer -> "THEME".equals(answer.questionId()))
                    .map(ScheduleCreateRequest.SelectedAnswer::answerId)
                    .map(this::themeReason)
                    .filter(Objects::nonNull)
                    .forEach(reasons::add);
            if (hasAnswer(request, "PROMPT_PREFER_SEA_VIEW")) {
                reasons.add("바다를 선호하는 추가 요청을 반영했습니다.");
            }
            if (hasAnswer(request, "PROMPT_PREFER_FOOD")) {
                reasons.add("음식 장소를 선호하는 추가 요청을 반영했습니다.");
            }
        }
        if (hasAnswer(request, "PROMPT_LOW_WALKING")) {
            reasons.add("도보 부담을 줄여 달라는 추가 요청을 반영했습니다.");
        }
        if (hasAnswer(request, "COMPANION_PARENTS") || hasAnswer(request, "COMPANION_FAMILY_WITH_CHILD")) {
            reasons.add("동행 조건을 고려해 무리한 이동을 줄이는 방향으로 배치했습니다.");
        }
        if (hasAnswer(request, "TRANSIT_SIMPLE")) {
            reasons.add("환승을 줄이는 선호 조건을 반영했습니다.");
        }
        if (MealTimePolicy.isMealPlace(place)) {
            reasons.add("점심 또는 저녁 식사 시간대에 이용할 수 있는 장소입니다.");
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
            case "THEME_SEA" -> "바다 테마와 일치하는 장소입니다.";
            case "THEME_SHOPPING" -> "쇼핑 테마와 일치하는 장소입니다.";
            case "THEME_HEALING" -> "휴식 테마와 일치하는 장소입니다.";
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
        if ("LAST_STOP".equals(day.getEndLocationSource())) {
            return day.getStartPlaceName() + " 출발 → " + stopNames;
        }
        return day.getStartPlaceName() + " 출발 → " + stopNames + " → " + day.getEndPlaceName() + " 도착";
    }

    private void resolvePlannerEndpoints(ScheduleDay day, List<Place> orderedPlaces) {
        if (orderedPlaces.isEmpty()) return;
        day.resolvePlannerEndpoints(orderedPlaces.get(0), orderedPlaces.get(orderedPlaces.size() - 1));
    }

    private boolean samePoint(TransitPoint left, TransitPoint right) {
        return left.longitude().compareTo(right.longitude()) == 0
                && left.latitude().compareTo(right.latitude()) == 0;
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
        List<MealTimePolicy.MealSlot> mealSlots = MealTimePolicy.activeSlots(day);
        Set<MealTimePolicy.MealSlot> assignedMealSlots = EnumSet.noneOf(MealTimePolicy.MealSlot.class);
        LocalTime cursor = day.getStartTime();
        for (ScheduleStop stop : day.getStops()) {
            TransitRoute inboundRoute = inboundRouteByStopId.get(stop.getId());
            if (inboundRoute != null) {
                cursor = cursor.plusMinutes(inboundRoute.getTotalMinutes());
            }
            LocalTime arriveAt = cursor;
            LocalTime departAt;
            if (stop.getFixedStartsAt() != null) {
                LocalTime fixedStart = stop.getFixedStartsAt()
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalTime();
                arriveAt = arriveAt.isBefore(fixedStart) ? fixedStart : arriveAt;
                departAt = stop.getFixedEndsAt()
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalTime();
                MealTimePolicy.Alignment alignment = MealTimePolicy.alignArrival(
                        arriveAt, stop.getPlace(), mealSlots, assignedMealSlots);
                if (alignment.slot() != null) assignedMealSlots.add(alignment.slot());
            } else {
                MealTimePolicy.Alignment alignment = MealTimePolicy.alignArrival(
                        arriveAt, stop.getPlace(), mealSlots, assignedMealSlots);
                arriveAt = alignment.arrival();
                if (alignment.slot() != null) assignedMealSlots.add(alignment.slot());
                departAt = arriveAt.plusMinutes(stop.getStayMinutes());
            }
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

        private String planningMode = "RULE_BASED";
        private Integer aiPlanConfidence;
        private int multiDayPlanCandidateCount;
        private int multiDayPlanRerankedCount;
        private int routeEstimateResolutionCount;
        private int routeEstimateCacheHitCount;
        private int providerEstimateCallCount;
        private int providerEstimateFailureCount;
        private int routeResolutionCount;
        private int routeCacheHitCount;
        private int providerCallCount;
        private int providerFailureCount;
    }

    private static final class RouteSearchContext {

        private final Map<RouteKey, TransitRouteEstimate> estimates = new HashMap<>();
        private final Map<RouteKey, TransitRouteResult> details = new HashMap<>();
        private int providerEstimateCount;

        private int providerEstimateCount() {
            return providerEstimateCount;
        }
    }

    private record RouteKey(TransitPoint origin, TransitPoint destination) {
    }

    private record StopTime(LocalTime arriveAt, LocalTime departAt) {
    }

    private record MultiDayPlanOrder(
            MultiDayPlanOptimizer.OptimizedPlan plan,
            List<List<Place>> orders
    ) {
    }

    private record MultiDayPlanResult(
            MultiDayPlanOptimizer.OptimizedPlan plan,
            List<List<Place>> orders,
            long actualCost
    ) {
    }

    private record SegmentMatch(TransitSegment segment, int nextIndex) {
    }

    private record PreviewPlanningOptions(
            SchedulePreview preview,
            List<SchedulePreviewResponse.ResolvedDay> resolvedDays,
            List<String> planningWarnings,
            List<SchedulePreviewCreateRequest.FixedEvent> fixedEvents,
            String customPrompt
    ) {
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
