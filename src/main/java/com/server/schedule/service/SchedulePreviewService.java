package com.server.schedule.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.PreviewAlreadyConsumedException;
import com.server.place.repository.PlaceRepository;
import com.server.question.entity.Question;
import com.server.question.repository.QuestionRepository;
import com.server.schedule.domain.SchedulePreview;
import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import com.server.schedule.planner.PlanningPromptInterpreter;
import com.server.schedule.planner.RuleBasedPlanningPromptInterpreter;
import com.server.schedule.planner.DailyScheduleTargetPolicy;
import com.server.schedule.repository.SchedulePreviewRepository;
import com.server.schedule.repository.ScheduleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulePreviewService {

    static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
    private static final int MAX_TRIP_DAYS = 4;
    private static final int MIN_AVAILABLE_MINUTES = 180;
    private static final int PREVIEW_EXPIRATION_MINUTES = 30;
    private static final LocalTime DEFAULT_DAY_START = LocalTime.of(10, 0);
    private static final LocalTime DEFAULT_DAY_END = LocalTime.of(20, 0);

    private final SchedulePreviewRepository previewRepository;
    private final ScheduleRepository scheduleRepository;
    private final QuestionRepository questionRepository;
    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlanningPromptInterpreter promptInterpreter;

    @Autowired
    public SchedulePreviewService(
            SchedulePreviewRepository previewRepository,
            ScheduleRepository scheduleRepository,
            QuestionRepository questionRepository,
            PlaceRepository placeRepository,
            ObjectMapper objectMapper,
            PlanningPromptInterpreter promptInterpreter
    ) {
        this(previewRepository, scheduleRepository, questionRepository, placeRepository,
                objectMapper, Clock.system(ZoneId.of(DEFAULT_TIME_ZONE)), promptInterpreter);
    }

    SchedulePreviewService(
            SchedulePreviewRepository previewRepository,
            ScheduleRepository scheduleRepository,
            QuestionRepository questionRepository,
            PlaceRepository placeRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(previewRepository, scheduleRepository, questionRepository, placeRepository,
                objectMapper, clock, new RuleBasedPlanningPromptInterpreter());
    }

    SchedulePreviewService(
            SchedulePreviewRepository previewRepository,
            ScheduleRepository scheduleRepository,
            QuestionRepository questionRepository,
            PlaceRepository placeRepository,
            ObjectMapper objectMapper,
            Clock clock,
            PlanningPromptInterpreter promptInterpreter
    ) {
        this.previewRepository = previewRepository;
        this.scheduleRepository = scheduleRepository;
        this.questionRepository = questionRepository;
        this.placeRepository = placeRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.promptInterpreter = promptInterpreter;
    }

    @Transactional
    public SchedulePreviewResponse create(SchedulePreviewCreateRequest request) {
        request = normalize(request);
        ValidationContext context = validate(request);
        Resolution resolution = resolve(request, context);
        OffsetDateTime now = OffsetDateTime.now(clock);
        String status = resolution.conflicts().isEmpty() ? "READY" : "REQUIRES_ACTION";

        SchedulePreview preview = new SchedulePreview(
                status,
                request.startDate(),
                request.endDate(),
                context.timeZone(),
                request.lodgingPlan().mode(),
                context.routeCoverage(),
                writeJson(request),
                writeJson(resolution.days()),
                resolution.endConstraint() == null ? null : writeJson(resolution.endConstraint()),
                writeJson(resolution.defaults()),
                writeJson(resolution.prompt()),
                writeJson(resolution.warnings()),
                writeJson(resolution.conflicts()),
                now.plusMinutes(PREVIEW_EXPIRATION_MINUTES),
                now
        );
        previewRepository.save(preview);
        return toResponse(preview, null);
    }

    private SchedulePreviewCreateRequest normalize(SchedulePreviewCreateRequest request) {
        if (request.lodgingPlan() != null) return request;
        return new SchedulePreviewCreateRequest(
                request.startDate(),
                request.endDate(),
                request.startLocation(),
                request.startTime(),
                new SchedulePreviewCreateRequest.LodgingPlan("UNDECIDED", null, null),
                request.endConstraint(),
                request.selectedAnswers(),
                request.mustVisitPlaceIds(),
                request.fixedEvents(),
                request.dayOverrides(),
                request.customPrompt(),
                request.timeZone()
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public SchedulePreviewResponse get(UUID previewId) {
        SchedulePreview preview = find(previewId);
        if (!"CONSUMED".equals(preview.getStatus()) && isExpired(preview)) {
            preview.expire();
            throw new BusinessException(ErrorCode.PREVIEW_EXPIRED);
        }
        UUID scheduleId = scheduleRepository.findIdByPreviewId(previewId).orElse(null);
        return toResponse(preview, scheduleId);
    }

    @Transactional(readOnly = true)
    public SchedulePreview requireGeneratable(UUID previewId) {
        SchedulePreview preview = find(previewId);
        if (isExpired(preview)) {
            throw new BusinessException(ErrorCode.PREVIEW_EXPIRED);
        }
        if ("CONSUMED".equals(preview.getStatus())) {
            throw new PreviewAlreadyConsumedException(
                    scheduleRepository.findIdByPreviewId(previewId).orElse(null));
        }
        if (!"READY".equals(preview.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_PREVIEW_REQUEST);
        }
        return preview;
    }

    public SchedulePreviewCreateRequest readInput(SchedulePreview preview) {
        return readJson(preview.getInputJson(), SchedulePreviewCreateRequest.class);
    }

    public List<SchedulePreviewResponse.ResolvedDay> readResolvedDays(SchedulePreview preview) {
        return readJson(preview.getResolvedDaysJson(), new TypeReference<>() { });
    }

    public List<SchedulePreviewResponse.Warning> readWarnings(SchedulePreview preview) {
        return readJson(preview.getWarningsJson(), new TypeReference<>() { });
    }

    public SchedulePreviewResponse.InterpretedPrompt readInterpretedPrompt(SchedulePreview preview) {
        return readJson(preview.getInterpretedPromptJson(),
                SchedulePreviewResponse.InterpretedPrompt.class);
    }

    private SchedulePreview find(UUID previewId) {
        return previewRepository.findById(previewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_PREVIEW_NOT_FOUND));
    }

    private boolean isExpired(SchedulePreview preview) {
        return "EXPIRED".equals(preview.getStatus())
                || OffsetDateTime.now(clock).isAfter(preview.getExpiresAt());
    }

    private ValidationContext validate(SchedulePreviewCreateRequest request) {
        if (request.startDate().isAfter(request.endDate())) invalid();
        int tripDays = (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (tripDays < 1 || tripDays > MAX_TRIP_DAYS) invalid();
        validateLocation(request.startLocation());

        String timeZone = request.timeZone() == null || request.timeZone().isBlank()
                ? DEFAULT_TIME_ZONE : request.timeZone();
        if (!DEFAULT_TIME_ZONE.equals(timeZone)) invalid();

        validateLodging(request, tripDays);
        validateEndConstraint(request);
        validateAnswers(request.selectedAnswers());
        validatePlaces(request, tripDays);
        validateOverrides(request);
        validateFixedEvents(request);
        String routeCoverage = "UNDECIDED".equals(request.lodgingPlan().mode())
                ? "ATTRACTION_ROUTES_ONLY" : "FULL";
        return new ValidationContext(tripDays, timeZone, routeCoverage);
    }

    private void validateLodging(SchedulePreviewCreateRequest request, int tripDays) {
        String mode = request.lodgingPlan().mode();
        switch (mode) {
            case "UNDECIDED" -> {
                if (request.lodgingPlan().baseLocation() != null
                        || !request.lodgingPlan().nightStaysOrEmpty().isEmpty()) invalid();
            }
            case "FIXED_BASE" -> {
                if (request.lodgingPlan().baseLocation() == null) {
                    throw new BusinessException(ErrorCode.FIXED_BASE_LOCATION_REQUIRED);
                }
                validateLocation(request.lodgingPlan().baseLocation());
            }
            case "PER_NIGHT" -> {
                Map<LocalDate, SchedulePreviewCreateRequest.NightStay> stays = request.lodgingPlan()
                        .nightStaysOrEmpty().stream()
                        .collect(Collectors.toMap(SchedulePreviewCreateRequest.NightStay::date,
                                Function.identity(), (left, right) -> { throw invalidException(); }));
                for (int index = 0; index < tripDays - 1; index++) {
                    SchedulePreviewCreateRequest.NightStay stay = stays.get(request.startDate().plusDays(index));
                    if (stay == null) throw new BusinessException(ErrorCode.PER_NIGHT_LOCATION_MISSING);
                    validateLocation(stay.location());
                }
                if (stays.size() != Math.max(0, tripDays - 1)) {
                    throw new BusinessException(ErrorCode.PER_NIGHT_LOCATION_MISSING);
                }
            }
            default -> invalid();
        }
    }

    private void validateEndConstraint(SchedulePreviewCreateRequest request) {
        if (request.endConstraint() == null) return;
        if (!Set.of("ARRIVE_BY", "TRAIN_DEPARTURE", "FLIGHT_DEPARTURE")
                .contains(request.endConstraint().type())) invalid();
        validateLocation(request.endConstraint().location());
        LocalDate targetDate = offsetDateTime(request.endConstraint().targetAt())
                .atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalDate();
        if (!targetDate.equals(request.endDate())) invalid();
    }

    private void validateAnswers(List<SchedulePreviewCreateRequest.SelectedAnswer> selectedAnswers) {
        Map<String, SchedulePreviewCreateRequest.SelectedAnswer> selections;
        try {
            selections = selectedAnswers.stream().collect(Collectors.toMap(
                    SchedulePreviewCreateRequest.SelectedAnswer::questionId,
                    Function.identity()
            ));
        } catch (IllegalStateException exception) {
            throw invalidException();
        }
        List<Question> activeQuestions = questionRepository.findByActiveTrueOrderByDisplayOrderAsc();
        Set<String> activeIds = activeQuestions.stream().map(Question::getId).collect(Collectors.toSet());
        if (!activeIds.containsAll(selections.keySet())) invalid();
        for (Question question : activeQuestions) {
            SchedulePreviewCreateRequest.SelectedAnswer selected = selections.get(question.getId());
            int count = selected == null ? 0 : new HashSet<>(selected.answerIds()).size();
            if (selected != null && count != selected.answerIds().size()) invalid();
            if (count < question.getMinSelections() || count > question.getMaxSelections()) invalid();
            if (selected != null) {
                Set<String> answerIds = question.getAnswers().stream()
                        .filter(answer -> answer.isActive())
                        .map(answer -> answer.getId())
                        .collect(Collectors.toSet());
                if (!answerIds.containsAll(selected.answerIds())) invalid();
            }
        }
    }

    private void validatePlaces(SchedulePreviewCreateRequest request, int tripDays) {
        Set<Long> ids = new LinkedHashSet<>(request.mustVisitPlaceIdsOrEmpty());
        request.fixedEventsOrEmpty().forEach(event -> ids.add(event.placeId()));
        if (ids.size() > tripDays * DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY) {
            throw new BusinessException(ErrorCode.MUST_VISIT_PLACE_LIMIT_EXCEEDED);
        }
        if (placeRepository.findAllById(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
    }

    private void validateOverrides(SchedulePreviewCreateRequest request) {
        Set<LocalDate> dates = new HashSet<>();
        for (SchedulePreviewCreateRequest.DayOverride override : request.dayOverridesOrEmpty()) {
            if (!dates.add(override.date()) || override.date().isBefore(request.startDate())
                    || override.date().isAfter(request.endDate())) invalid();
            if (override.availableFrom() != null && override.availableUntil() != null
                    && !override.availableFrom().isBefore(override.availableUntil())) invalid();
            if (override.startLocation() != null) validateLocation(override.startLocation());
            if (override.endLocation() != null) validateLocation(override.endLocation());
            if (request.endConstraint() != null && override.date().equals(request.endDate())
                    && override.endLocation() != null) invalid();
        }
    }

    private void validateFixedEvents(SchedulePreviewCreateRequest request) {
        Set<String> clientIds = new HashSet<>();
        Set<Long> placeIds = new HashSet<>();
        Map<LocalDate, Integer> eventCountByDate = new HashMap<>();
        for (SchedulePreviewCreateRequest.FixedEvent event : request.fixedEventsOrEmpty()) {
            OffsetDateTime startsAt = offsetDateTime(event.startsAt());
            OffsetDateTime endsAt = offsetDateTime(event.endsAt());
            if (!clientIds.add(event.clientEventId()) || !placeIds.add(event.placeId())
                    || !startsAt.isBefore(endsAt)) invalid();
            LocalDate date = startsAt.atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalDate();
            LocalDate endDate = endsAt.atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalDate();
            if (!date.equals(endDate) || date.isBefore(request.startDate()) || date.isAfter(request.endDate())) invalid();
            if (eventCountByDate.merge(date, 1, Integer::sum)
                    > DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY) {
                throw new BusinessException(ErrorCode.MUST_VISIT_PLACE_LIMIT_EXCEEDED);
            }
        }
    }

    private Resolution resolve(SchedulePreviewCreateRequest request, ValidationContext context) {
        List<SchedulePreviewResponse.AppliedDefault> defaults = new ArrayList<>();
        Map<LocalDate, SchedulePreviewCreateRequest.DayOverride> overrides = request.dayOverridesOrEmpty()
                .stream().collect(Collectors.toMap(SchedulePreviewCreateRequest.DayOverride::date, Function.identity()));
        Map<LocalDate, SchedulePreviewCreateRequest.Location> stays = request.lodgingPlan().nightStaysOrEmpty()
                .stream().collect(Collectors.toMap(SchedulePreviewCreateRequest.NightStay::date,
                        SchedulePreviewCreateRequest.NightStay::location));
        SchedulePreviewResponse.ResolvedEndConstraint resolvedEnd = resolveEndConstraint(request, defaults);
        List<SchedulePreviewResponse.ResolvedDay> days = new ArrayList<>();
        List<SchedulePreviewResponse.Conflict> conflicts = fixedEventConflicts(request);

        for (int index = 0; index < context.tripDays(); index++) {
            LocalDate date = request.startDate().plusDays(index);
            SchedulePreviewCreateRequest.DayOverride override = overrides.get(date);
            LocalTime from = resolveAvailableFrom(request, date, index, override, defaults);
            LocalTime until = override != null && override.availableUntil() != null
                    ? override.availableUntil() : DEFAULT_DAY_END;
            if (index == context.tripDays() - 1 && resolvedEnd != null
                    && resolvedEnd.availableUntil().isBefore(until)) {
                until = resolvedEnd.availableUntil();
            }

            LocationResolution start = resolveStartLocation(request, date, index, override, stays);
            LocationResolution end = resolveEndLocation(request, date, index, context.tripDays(), override, stays);
            if (index == context.tripDays() - 1 && request.endConstraint() != null) {
                end = new LocationResolution(request.endConstraint().location(), "END_CONSTRAINT");
            }
            int available = (int) Duration.between(from, until).toMinutes();
            if (available < MIN_AVAILABLE_MINUTES) {
                String field = "dayOverrides[" + date + "].availableUntil";
                conflicts.add(new SchedulePreviewResponse.Conflict(
                        "INSUFFICIENT_AVAILABLE_TIME",
                        date + "에 일정을 구성할 시간이 부족합니다.",
                        field,
                        date,
                        MIN_AVAILABLE_MINUTES,
                        Math.max(0, available),
                        List.of(field)
                ));
            }
            days.add(new SchedulePreviewResponse.ResolvedDay(
                    date,
                    from,
                    until,
                    toResponseLocation(start.location()),
                    toResponseLocation(end.location()),
                    start.source(),
                    end.source()
            ));
        }
        conflicts.addAll(fixedEventAvailabilityConflicts(request, days));

        List<SchedulePreviewResponse.Warning> warnings = new ArrayList<>();
        if ("UNDECIDED".equals(request.lodgingPlan().mode())) {
            warnings.add(new SchedulePreviewResponse.Warning(
                    "LODGING_ROUTE_EXCLUDED", null, "숙소 이동시간은 일정 경로에 포함되지 않습니다."));
        }
        return new Resolution(
                List.copyOf(days), resolvedEnd, List.copyOf(defaults), promptInterpreter.interpret(request.customPrompt()),
                List.copyOf(warnings), List.copyOf(conflicts));
    }

    private LocalTime resolveAvailableFrom(
            SchedulePreviewCreateRequest request,
            LocalDate date,
            int dayIndex,
            SchedulePreviewCreateRequest.DayOverride override,
            List<SchedulePreviewResponse.AppliedDefault> defaults
    ) {
        if (override != null && override.availableFrom() != null) return override.availableFrom();
        if (dayIndex > 0) {
            defaults.add(new SchedulePreviewResponse.AppliedDefault(
                    "resolvedDays[" + dayIndex + "].availableFrom", DEFAULT_DAY_START, "DEFAULT_FULL_DAY_START"));
            return DEFAULT_DAY_START;
        }
        if (request.startTime() != null) return request.startTime();
        LocalDate today = LocalDate.now(clock);
        LocalTime resolved = date.equals(today) ? roundUpToHalfHour(LocalTime.now(clock)) : DEFAULT_DAY_START;
        defaults.add(new SchedulePreviewResponse.AppliedDefault(
                "resolvedDays[0].availableFrom", resolved,
                date.equals(today) ? "DEFAULT_CURRENT_TIME" : "DEFAULT_FULL_DAY_START"));
        return resolved;
    }

    private SchedulePreviewResponse.ResolvedEndConstraint resolveEndConstraint(
            SchedulePreviewCreateRequest request,
            List<SchedulePreviewResponse.AppliedDefault> defaults
    ) {
        if (request.endConstraint() == null) return null;
        int buffer = request.endConstraint().bufferMinutes() == null
                ? defaultBuffer(request.endConstraint().type()) : request.endConstraint().bufferMinutes();
        if (request.endConstraint().bufferMinutes() == null) {
            defaults.add(new SchedulePreviewResponse.AppliedDefault(
                    "endConstraint.bufferMinutes", buffer, "DEFAULT_" + request.endConstraint().type() + "_BUFFER"));
        }
        ZonedDateTime localTarget = offsetDateTime(request.endConstraint().targetAt())
                .atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE));
        return new SchedulePreviewResponse.ResolvedEndConstraint(
                request.endConstraint().type(), request.endConstraint().targetAt(), buffer,
                localTarget.minusMinutes(buffer).toLocalTime());
    }

    private int defaultBuffer(String type) {
        return switch (type) {
            case "TRAIN_DEPARTURE" -> 30;
            case "FLIGHT_DEPARTURE" -> 90;
            default -> 0;
        };
    }

    private LocationResolution resolveStartLocation(
            SchedulePreviewCreateRequest request,
            LocalDate date,
            int dayIndex,
            SchedulePreviewCreateRequest.DayOverride override,
            Map<LocalDate, SchedulePreviewCreateRequest.Location> stays
    ) {
        if (override != null && override.startLocation() != null) {
            return new LocationResolution(override.startLocation(), "DAY_OVERRIDE");
        }
        if (dayIndex == 0) return new LocationResolution(request.startLocation(), "USER");
        return switch (request.lodgingPlan().mode()) {
            case "FIXED_BASE" -> new LocationResolution(request.lodgingPlan().baseLocation(), "LODGING");
            case "PER_NIGHT" -> new LocationResolution(stays.get(date.minusDays(1)), "LODGING");
            default -> new LocationResolution(null, "PLANNER_DECIDES");
        };
    }

    private LocationResolution resolveEndLocation(
            SchedulePreviewCreateRequest request,
            LocalDate date,
            int dayIndex,
            int tripDays,
            SchedulePreviewCreateRequest.DayOverride override,
            Map<LocalDate, SchedulePreviewCreateRequest.Location> stays
    ) {
        if (override != null && override.endLocation() != null) {
            return new LocationResolution(override.endLocation(), "DAY_OVERRIDE");
        }
        if ("FIXED_BASE".equals(request.lodgingPlan().mode())) {
            return new LocationResolution(request.lodgingPlan().baseLocation(), "LODGING");
        }
        if ("PER_NIGHT".equals(request.lodgingPlan().mode()) && dayIndex < tripDays - 1) {
            return new LocationResolution(stays.get(date), "LODGING");
        }
        return new LocationResolution(null, "PLANNER_DECIDES");
    }

    private List<SchedulePreviewResponse.Conflict> fixedEventConflicts(SchedulePreviewCreateRequest request) {
        List<SchedulePreviewResponse.Conflict> conflicts = new ArrayList<>();
        List<SchedulePreviewCreateRequest.FixedEvent> events = request.fixedEventsOrEmpty().stream()
                .sorted((left, right) -> offsetDateTime(left.startsAt()).compareTo(offsetDateTime(right.startsAt())))
                .toList();
        for (int index = 1; index < events.size(); index++) {
            SchedulePreviewCreateRequest.FixedEvent previous = events.get(index - 1);
            SchedulePreviewCreateRequest.FixedEvent current = events.get(index);
            if (offsetDateTime(current.startsAt()).isBefore(offsetDateTime(previous.endsAt()))) {
                LocalDate date = offsetDateTime(current.startsAt())
                        .atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalDate();
                conflicts.add(new SchedulePreviewResponse.Conflict(
                        "FIXED_EVENT_CONFLICT", "고정 행사 시간이 겹칩니다.", "fixedEvents",
                        date, null, null, List.of("fixedEvents")));
            }
        }
        return conflicts;
    }

    private List<SchedulePreviewResponse.Conflict> fixedEventAvailabilityConflicts(
            SchedulePreviewCreateRequest request,
            List<SchedulePreviewResponse.ResolvedDay> days
    ) {
        Map<LocalDate, SchedulePreviewResponse.ResolvedDay> dayByDate = days.stream()
                .collect(Collectors.toMap(SchedulePreviewResponse.ResolvedDay::date, Function.identity()));
        List<SchedulePreviewResponse.Conflict> conflicts = new ArrayList<>();
        for (SchedulePreviewCreateRequest.FixedEvent event : request.fixedEventsOrEmpty()) {
            OffsetDateTime startsAt = offsetDateTime(event.startsAt());
            OffsetDateTime endsAt = offsetDateTime(event.endsAt());
            LocalDate date = startsAt.atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalDate();
            LocalTime starts = startsAt.atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalTime();
            LocalTime ends = endsAt.atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalTime();
            SchedulePreviewResponse.ResolvedDay day = dayByDate.get(date);
            if (day != null && (starts.isBefore(day.availableFrom()) || ends.isAfter(day.availableUntil()))) {
                conflicts.add(new SchedulePreviewResponse.Conflict(
                        "FIXED_EVENT_CONFLICT", "고정 행사가 해당 날짜의 활동 가능 시간을 벗어납니다.",
                        "fixedEvents[" + event.clientEventId() + "]", date, null, null,
                        List.of("fixedEvents[" + event.clientEventId() + "]", "dayOverrides[" + date + "]")));
            }
        }
        return conflicts;
    }

    private SchedulePreviewResponse.Location toResponseLocation(SchedulePreviewCreateRequest.Location location) {
        if (location == null) return null;
        return new SchedulePreviewResponse.Location(
                location.name(), location.address(), location.longitude(), location.latitude());
    }

    private LocalTime roundUpToHalfHour(LocalTime time) {
        LocalTime truncated = time.truncatedTo(ChronoUnit.MINUTES);
        int remainder = truncated.getMinute() % 30;
        if (remainder == 0 && time.getSecond() == 0 && time.getNano() == 0) return truncated;
        return truncated.plusMinutes(remainder == 0 ? 30 : 30 - remainder).withSecond(0).withNano(0);
    }

    private void validateLocation(SchedulePreviewCreateRequest.Location location) {
        if (location.longitude().compareTo(new BigDecimal("-180")) < 0
                || location.longitude().compareTo(new BigDecimal("180")) > 0
                || location.latitude().compareTo(new BigDecimal("-90")) < 0
                || location.latitude().compareTo(new BigDecimal("90")) > 0) invalid();
    }

    private OffsetDateTime offsetDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw invalidException();
        }
    }

    private SchedulePreviewResponse toResponse(SchedulePreview preview, UUID scheduleId) {
        return new SchedulePreviewResponse(
                preview.getId(), preview.getStatus(), "READY".equals(preview.getStatus()), preview.getExpiresAt(),
                preview.getTimeZone(), preview.getLodgingMode(), preview.getRouteCoverage(),
                readJson(preview.getResolvedDaysJson(), new TypeReference<>() { }),
                preview.getResolvedEndConstraintJson() == null ? null
                        : readJson(preview.getResolvedEndConstraintJson(), SchedulePreviewResponse.ResolvedEndConstraint.class),
                readJson(preview.getAppliedDefaultsJson(), new TypeReference<>() { }),
                readJson(preview.getInterpretedPromptJson(), SchedulePreviewResponse.InterpretedPrompt.class),
                readJson(preview.getWarningsJson(), new TypeReference<>() { }),
                readJson(preview.getConflictsJson(), new TypeReference<>() { }),
                scheduleId
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize schedule preview", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize schedule preview", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize schedule preview", exception);
        }
    }

    private void invalid() {
        throw invalidException();
    }

    private BusinessException invalidException() {
        return new BusinessException(ErrorCode.INVALID_SCHEDULE_PREVIEW_REQUEST);
    }

    private record ValidationContext(int tripDays, String timeZone, String routeCoverage) { }
    private record LocationResolution(SchedulePreviewCreateRequest.Location location, String source) { }
    private record Resolution(
            List<SchedulePreviewResponse.ResolvedDay> days,
            SchedulePreviewResponse.ResolvedEndConstraint endConstraint,
            List<SchedulePreviewResponse.AppliedDefault> defaults,
            SchedulePreviewResponse.InterpretedPrompt prompt,
            List<SchedulePreviewResponse.Warning> warnings,
            List<SchedulePreviewResponse.Conflict> conflicts
    ) { }
}
