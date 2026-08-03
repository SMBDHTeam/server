package com.server.common.error;

/**
 * A specific reason a request was rejected, carried on {@link BusinessException} so the
 * client is told which field to fix instead of receiving a bare error code.
 *
 * <p>{@code field} uses the request's JSON path (for example {@code startLocation.longitude}
 * or {@code selectedAnswers}) so a form can highlight the offending input directly.
 */
public record FieldViolation(String field, String message) {

    public static FieldViolation of(String field, String message) {
        return new FieldViolation(field, message);
    }
}
