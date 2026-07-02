package com.server.question.dto;

import com.server.answer.dto.AnswerResponse;

import java.util.List;

public record QuestionResponse(String id, String text, String type, boolean required, int displayOrder, List<AnswerResponse> answers) {
}
