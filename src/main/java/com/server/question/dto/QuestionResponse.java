package com.server.question.dto;

import com.server.answer.dto.AnswerResponse;

import java.util.List;

public record QuestionResponse(
        String id,
        String text,
        String type,
        boolean required,
        int minSelections,
        int maxSelections,
        int uiStep,
        int displayOrder,
        List<AnswerResponse> answers
) {
    public QuestionResponse(
            String id,
            String text,
            String type,
            boolean required,
            int displayOrder,
            List<AnswerResponse> answers
    ) {
        this(id, text, type, required, required ? 1 : 0, 1, 1, displayOrder, answers);
    }
}
