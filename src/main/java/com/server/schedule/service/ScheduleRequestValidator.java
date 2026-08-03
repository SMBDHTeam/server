package com.server.schedule.service;

import com.server.answer.entity.Answer;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.FieldViolation;
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
import java.util.stream.Collectors;
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
        validateLocation(request.startLocation(), "startLocation");
        validateLocation(request.endLocation(), "endLocation");
        for (int index = 0; index < request.daysOrEmpty().size(); index++) {
            ScheduleCreateRequest.DayCondition day = request.daysOrEmpty().get(index);
            validateLocation(day.startLocation(), "days[" + index + "].startLocation");
            validateLocation(day.endLocation(), "days[" + index + "].endLocation");
        }
        validateSelectedAnswers(request.selectedAnswers());
        validateMustVisitPlaceIds(request.mustVisitPlaceIdsOrEmpty(), tripDays);
    }

    private int validateDateAndDayConditions(ScheduleCreateRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            invalid("endDate", "종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (!request.dailyEndTime().isAfter(request.dailyStartTime())) {
            invalid("dailyEndTime", "하루 종료 시간은 시작 시간보다 늦어야 합니다.");
        }
        long tripDayCount = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (tripDayCount > MAX_TRIP_DAYS) {
            invalid("endDate", "여행 기간은 최대 " + MAX_TRIP_DAYS + "일입니다. 요청 " + tripDayCount + "일");
        }
        int tripDays = (int) tripDayCount;
        if (request.daysOrEmpty().isEmpty()) {
            return tripDays;
        }
        Set<Integer> dayNumbers = new HashSet<>();
        for (int index = 0; index < request.daysOrEmpty().size(); index++) {
            ScheduleCreateRequest.DayCondition day = request.daysOrEmpty().get(index);
            String path = "days[" + index + "]";
            if (!day.endTime().isAfter(day.startTime())) {
                invalid(path + ".endTime", "종료 시간은 시작 시간보다 늦어야 합니다.");
            }
            if (day.dayNo() > tripDays) {
                invalid(path + ".dayNo", "여행 기간(" + tripDays + "일)을 벗어난 일차입니다.");
            }
            if (!dayNumbers.add(day.dayNo())) {
                invalid(path + ".dayNo", "중복된 일차입니다: " + day.dayNo());
            }
        }
        if (dayNumbers.size() != tripDays) {
            invalid("days", "모든 일차(" + tripDays + "일)의 조건을 보내야 합니다. 현재 " + dayNumbers.size() + "일");
        }
        return tripDays;
    }

    /**
     * Coordinates must be WGS84 degrees. Provider specific grids such as Naver's TM128
     * fall far outside this range, so the message names the value to make that obvious.
     */
    private void validateLocation(ScheduleCreateRequest.Location location, String field) {
        if (location.longitude().compareTo(MIN_LONGITUDE) < 0
                || location.longitude().compareTo(MAX_LONGITUDE) > 0) {
            invalid(field + ".longitude",
                    "경도는 WGS84 기준 -180~180 이어야 합니다. 요청 값: " + location.longitude().toPlainString());
        }
        if (location.latitude().compareTo(MIN_LATITUDE) < 0
                || location.latitude().compareTo(MAX_LATITUDE) > 0) {
            invalid(field + ".latitude",
                    "위도는 WGS84 기준 -90~90 이어야 합니다. 요청 값: " + location.latitude().toPlainString());
        }
    }

    private void validateSelectedAnswers(List<ScheduleCreateRequest.SelectedAnswer> selectedAnswers) {
        Map<String, List<ScheduleCreateRequest.SelectedAnswer>> answersByQuestion = selectedAnswers.stream()
                .collect(Collectors.groupingBy(ScheduleCreateRequest.SelectedAnswer::questionId));
        Set<String> selectedQuestionIds = answersByQuestion.keySet();
        if (questionRepository == null) {
            return;
        }

        List<Question> questions = questionRepository.findByActiveTrueOrderByDisplayOrderAsc();
        Map<String, Question> questionById = new HashMap<>();
        questions.forEach(question -> questionById.put(question.getId(), question));
        for (Map.Entry<String, List<ScheduleCreateRequest.SelectedAnswer>> entry : answersByQuestion.entrySet()) {
            Question question = questionById.get(entry.getKey());
            String path = "selectedAnswers[questionId=" + entry.getKey() + "]";
            if (question == null) {
                invalid(path, "존재하지 않는 질문입니다: " + entry.getKey());
            }
            Set<String> distinctAnswerIds = new HashSet<>();
            for (ScheduleCreateRequest.SelectedAnswer selectedAnswer : entry.getValue()) {
                if (!distinctAnswerIds.add(selectedAnswer.answerId())) {
                    invalid(path, "중복된 답변입니다: " + selectedAnswer.answerId());
                }
                if (!containsActiveAnswer(question, selectedAnswer.answerId())) {
                    invalid(path, "이 질문의 답변이 아닙니다: " + selectedAnswer.answerId());
                }
            }
            int selectedCount = distinctAnswerIds.size();
            if (selectedCount < question.getMinSelections() || selectedCount > question.getMaxSelections()) {
                invalid(path, "선택 개수는 " + question.getMinSelections() + "~" + question.getMaxSelections()
                        + "개여야 합니다. 현재 " + selectedCount + "개");
            }
        }
        List<String> missingRequiredQuestionIds = questions.stream()
                .filter(Question::isRequired)
                .map(Question::getId)
                .filter(questionId -> !selectedQuestionIds.contains(questionId))
                .toList();
        if (!missingRequiredQuestionIds.isEmpty()) {
            invalid("selectedAnswers",
                    "필수 질문에 대한 답변이 없습니다: " + String.join(", ", missingRequiredQuestionIds));
        }
    }

    private boolean containsActiveAnswer(Question question, String answerId) {
        return question.getAnswers()
                .stream()
                .filter(Answer::isActive)
                .anyMatch(answer -> answer.getId().equals(answerId));
    }

    private void validateMustVisitPlaceIds(List<Long> placeIds, int tripDays) {
        int maximum = tripDays * DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY;
        if (placeIds.size() > maximum) {
            invalid("mustVisitPlaceIds",
                    "필수 방문지는 최대 " + maximum + "개입니다. 요청 " + placeIds.size() + "개");
        }
        if (placeIds.stream().anyMatch(placeId -> placeId == null || placeId <= 0)) {
            invalid("mustVisitPlaceIds", "장소 ID는 1 이상이어야 합니다.");
        }
        if (new HashSet<>(placeIds).size() != placeIds.size()) {
            invalid("mustVisitPlaceIds", "중복된 장소 ID가 있습니다.");
        }
    }

    private void invalid(String field, String message) {
        throw new BusinessException(
                ErrorCode.INVALID_SCHEDULE_CONDITION, List.of(FieldViolation.of(field, message)));
    }
}
