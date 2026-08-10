package com.server.schedule.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.FieldViolation;
import com.server.common.error.PreviewAlreadyConsumedException;
import com.server.external.schedule.FastApiScheduleClient;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    private FastApiScheduleClient fastApiScheduleClient;

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

    @Autowired(required = false)
    void setFastApiScheduleClient(FastApiScheduleClient fastApiScheduleClient) {
        this.fastApiScheduleClient = fastApiScheduleClient;
    }

    @Transactional
    public SchedulePreviewResponse create(SchedulePreviewCreateRequest request) {
        return requireFastApiScheduleClient().createPreview(request);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public SchedulePreviewResponse get(UUID previewId) {
        return requireFastApiScheduleClient().getPreview(previewId);
    }

    private FastApiScheduleClient requireFastApiScheduleClient() {
        if (fastApiScheduleClient == null || !fastApiScheduleClient.enabled()) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        return fastApiScheduleClient;
    }
}
