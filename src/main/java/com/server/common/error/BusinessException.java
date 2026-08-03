package com.server.common.error;

import java.util.List;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldViolation> fieldViolations;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public BusinessException(ErrorCode errorCode, List<FieldViolation> fieldViolations) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.fieldViolations = fieldViolations == null ? List.of() : List.copyOf(fieldViolations);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.fieldViolations = List.of();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Empty when the failure cannot be attributed to specific request fields. */
    public List<FieldViolation> getFieldViolations() {
        return fieldViolations;
    }
}
