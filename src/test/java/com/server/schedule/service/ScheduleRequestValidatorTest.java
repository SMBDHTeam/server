package com.server.schedule.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.server.answer.entity.Answer;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.question.entity.Question;
import com.server.question.repository.QuestionRepository;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleRequestValidatorTest {

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final ScheduleRequestValidator validator = new ScheduleRequestValidator(questionRepository);

    @Test
    void acceptsActiveAnswersForEveryRequiredQuestion() {
        List<Question> questions = List.of(
                question("COMPANION", "COMPANION_FRIENDS"),
                question("PACE", "PACE_BALANCED"),
                question("THEME", "THEME_LOCAL"),
                question("MOBILITY", "MOBILITY_NORMAL"),
                question("TRANSIT", "TRANSIT_SIMPLE")
        );
        when(questionRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(questions);

        assertThatCode(() -> validator.validate(validRequest())).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingRequiredQuestion() {
        List<Question> questions = List.of(
                question("COMPANION", "COMPANION_FRIENDS"),
                question("PACE", "PACE_BALANCED"),
                question("THEME", "THEME_LOCAL"),
                question("MOBILITY", "MOBILITY_NORMAL"),
                question("TRANSIT", "TRANSIT_SIMPLE")
        );
        when(questionRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(questions);
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                location("부산역", "129.0403", "35.1151"),
                location("부산역", "129.0403", "35.1151"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS")),
                List.of()
        );

        assertInvalid(request);
    }

    @Test
    void rejectsAnswerThatDoesNotBelongToQuestion() {
        List<Question> questions = List.of(
                question("COMPANION", "COMPANION_FRIENDS"),
                question("PACE", "PACE_BALANCED"),
                question("THEME", "THEME_LOCAL"),
                question("MOBILITY", "MOBILITY_NORMAL"),
                question("TRANSIT", "TRANSIT_SIMPLE")
        );
        when(questionRepository.findByActiveTrueOrderByDisplayOrderAsc()).thenReturn(questions);
        ScheduleCreateRequest request = validRequest();
        List<ScheduleCreateRequest.SelectedAnswer> invalidAnswers = request.selectedAnswers().stream()
                .map(answer -> "PACE".equals(answer.questionId())
                        ? new ScheduleCreateRequest.SelectedAnswer("PACE", "THEME_LOCAL")
                        : answer)
                .toList();

        assertInvalid(new ScheduleCreateRequest(
                request.startDate(),
                request.endDate(),
                request.dailyStartTime(),
                request.dailyEndTime(),
                request.startLocation(),
                request.endLocation(),
                invalidAnswers,
                request.mustVisitPlaceIds()
        ));
    }

    private void assertInvalid(ScheduleCreateRequest request) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);
    }

    private ScheduleCreateRequest validRequest() {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                location("부산역", "129.0403", "35.1151"),
                location("부산역", "129.0403", "35.1151"),
                List.of(
                        new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                        new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_BALANCED"),
                        new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_LOCAL"),
                        new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_NORMAL"),
                        new ScheduleCreateRequest.SelectedAnswer("TRANSIT", "TRANSIT_SIMPLE")
                ),
                List.of()
        );
    }

    private Question question(String questionId, String answerId) {
        Question question = mock(Question.class);
        Answer answer = mock(Answer.class);
        when(question.getId()).thenReturn(questionId);
        when(question.isRequired()).thenReturn(true);
        when(question.getAnswers()).thenReturn(List.of(answer));
        when(answer.getId()).thenReturn(answerId);
        when(answer.isActive()).thenReturn(true);
        return question;
    }

    private ScheduleCreateRequest.Location location(String name, String longitude, String latitude) {
        return new ScheduleCreateRequest.Location(name, new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
