package com.server.question.service;

import com.server.answer.dto.AnswerResponse;
import com.server.answer.entity.Answer;
import com.server.question.dto.QuestionResponse;
import com.server.question.dto.TripQuestionsResponse;
import com.server.question.entity.Question;
import com.server.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    private AnswerResponse toAnswerResponse(Answer answer) {
        return new AnswerResponse(
                answer.getId(),
                answer.getLabel(),
                answer.getDisplayOrder()
        );
    }

    private QuestionResponse toQuestionResponse(Question question) {
        List<AnswerResponse> answers = question.getAnswers().stream()
                .filter(Answer::isActive)
                .sorted(Comparator.comparingInt(Answer::getDisplayOrder))
                .map(this::toAnswerResponse)
                .toList();

        return new QuestionResponse(
                question.getId(),
                question.getText(),
                question.getType(),
                question.isRequired(),
                question.getMinSelections(),
                question.getMaxSelections(),
                question.getUiStep(),
                question.getDisplayOrder(),
                answers
        );
    }

    @Transactional(readOnly = true)
    public TripQuestionsResponse getTripQuestions() {
        List<QuestionResponse> items = questionRepository
                .findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toQuestionResponse)
                .toList();

        return new TripQuestionsResponse(items);

    }
}
