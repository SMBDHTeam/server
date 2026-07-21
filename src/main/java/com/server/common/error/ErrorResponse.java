package com.server.common.error;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors,
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID scheduleId
) {

    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of(), traceId, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fieldErrors, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors, traceId, null);
    }

    public static ErrorResponse consumed(ErrorCode errorCode, String traceId, UUID scheduleId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of(), traceId, scheduleId);
    }

    public record FieldErrorResponse(
            String field,
            String message
    ) {
    }
}
