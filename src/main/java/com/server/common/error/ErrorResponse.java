package com.server.common.error;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors,
        String traceId
) {

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of(), traceId);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fieldErrors, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors, traceId);
    }

    public record FieldErrorResponse(
            String field,
            String message
    ) {
    }
}
