package com.server.question.controller;

import com.server.question.dto.TripQuestionsResponse;
import com.server.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping("/api/v1/trip-questions")
    public TripQuestionsResponse gettripQuestionsResponse() {
        return questionService.getTripQuestions();
    }


}
