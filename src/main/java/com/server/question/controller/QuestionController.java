package com.server.question.controller;

import com.server.question.dto.TripQuestionsResponse;
import com.server.question.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "여행 질문", description = "일정 생성에 사용하는 질문과 답변 ID")
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping("/api/v1/trip-questions")
    @Operation(summary = "여행 질문 조회")
    public TripQuestionsResponse gettripQuestionsResponse() {
        return questionService.getTripQuestions();
    }


}
