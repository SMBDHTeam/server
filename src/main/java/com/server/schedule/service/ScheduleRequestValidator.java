package com.server.schedule.service;

import com.server.answer.entity.Answer;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.question.entity.Question;
import com.server.question.repository.QuestionRepository;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.planner.DailyScheduleTargetPolicy;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScheduleRequestValidator {

    private static final int MAX_TRIP_DAYS = 4;
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");

    private final QuestionRepository questionRepository;

    public ScheduleRequestValidator() {
        this.questionRepository = null;
    }

    @Autowired
    public ScheduleRequestValidator(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public void validate(ScheduleCreateRequest request) {
        int tripDays = validateDateAndDayConditions(request);
        validateLocation(request.startLocation());
        validateLocation(request.endLocation());
        request.daysOrEmpty().forEach(day -> {
            validateLocation(day.startLocation());
            validateLocation(day.endLocation());
        });
        validateSelectedAnswers(request.selectedAnswers());
        validateMustVisitPlaceIds(request.mustVisitPlaceIdsOrEmpty(), tripDays);
    }

    private int validateDateAndDayConditions(ScheduleCreateRequest request) {
        if (request.endDate().isBefore(request.startDate())
                || !request.dailyEndTime().isAfter(request.dailyStartTime())) {
            invalid();
        }
        long tripDayCount = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (tripDayCount > MAX_TRIP_DAYS) {
            invalid();
        }
        int tripDays = (int) tripDayCount;
        if (request.daysOrEmpty().isEmpty()) {
            return tripDays;
        }
        Set<Integer> dayNumbers = new HashSet<>();
        for (ScheduleCreateRequest.DayCondition day : request.daysOrEmpty()) {
            if (!day.endTime().isAfter(day.startTime())
                    || day.dayNo() > tripDays
                    || !dayNumbers.add(day.dayNo())) {
                invalid();
            }
        }
        if (dayNumbers.size() != tripDays) {
            invalid();
        }
        return tripDays;
    }

    private void validateLocation(ScheduleCreateRequest.Location location) {
        if (location.longitude().compareTo(MIN_LONGITUDE) < 0
                || location.longitude().compareTo(MAX_LONGITUDE) > 0
                || location.latitude().compareTo(MIN_LATITUDE) < 0
                || location.latitude().compareTo(MAX_LATITUDE) > 0) {
            invalid();
        }
    }

    private void validateSelectedAnswers(List<ScheduleCreateRequest.SelectedAnswer> selectedAnswers) {
        Set<String> selectedQuestionIds = new HashSet<>();
        for (ScheduleCreateRequest.SelectedAnswer selectedAnswer : selectedAnswers) {
            if (!selectedQuestionIds.add(selectedAnswer.questionId())) {
                invalid();
            }
        }
        if (questionRepository == null) {
            return;
        }

        List<Question> questions = questionRepository.findByActiveTrueOrderByDisplayOrderAsc();
        Map<String, Question> questionById = new HashMap<>();
        questions.forEach(question -> questionById.put(question.getId(), question));
        for (ScheduleCreateRequest.SelectedAnswer selectedAnswer : selectedAnswers) {
            Question question = questionById.get(selectedAnswer.questionId());
            if (question == null || !containsActiveAnswer(question, selectedAnswer.answerId())) {
                invalid();
            }
        }
        boolean missingRequiredQuestion = questions.stream()
                .filter(Question::isRequired)
                .anyMatch(question -> !selectedQuestionIds.contains(question.getId()));
        if (missingRequiredQuestion) {
            invalid();
        }
    }

    private boolean containsActiveAnswer(Question question, String answerId) {
        return question.getAnswers()
                .stream()
                .filter(Answer::isActive)
                .anyMatch(answer -> answer.getId().equals(answerId));
    }

    private void validateMustVisitPlaceIds(List<Long> placeIds, int tripDays) {
        if (placeIds.size() > tripDays * DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY
                || placeIds.stream().anyMatch(placeId -> placeId == null || placeId <= 0)
                || new HashSet<>(placeIds).size() != placeIds.size()) {
            invalid();
        }
    }

    private void invalid() {
        throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
    }
}
