package com.server.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_ATTRIBUTE = "traceId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, traceId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorResponse)
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_SCHEDULE_CONDITION, fieldErrors, traceId(request)));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldErrorResponse> fieldErrors = exception.getAllErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldErrorResponse(
                        error.getCodes() == null || error.getCodes().length == 0 ? "" : error.getCodes()[0],
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_SCHEDULE_CONDITION, fieldErrors, traceId(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldErrorResponse> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ErrorResponse.FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_SCHEDULE_CONDITION, fieldErrors, traceId(request)));
    }

    private ErrorResponse.FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return new ErrorResponse.FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
        return traceId == null ? "" : traceId.toString();
    }
}
