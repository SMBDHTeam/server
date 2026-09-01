package com.server.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException.Reference;
import tools.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TRACE_ID_ATTRIBUTE = "traceId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        List<ErrorResponse.FieldErrorResponse> fieldErrors = exception.getFieldViolations()
                .stream()
                .map(violation -> new ErrorResponse.FieldErrorResponse(
                        violation.field(), violation.message()))
                .toList();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, fieldErrors, traceId(request)));
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
                .body(ErrorResponse.of(validationErrorCode(request), fieldErrors, traceId(request)));
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
                .body(ErrorResponse.of(validationErrorCode(request), fieldErrors, traceId(request)));
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
                .body(ErrorResponse.of(validationErrorCode(request), fieldErrors, traceId(request)));
    }

    @ExceptionHandler(PreviewAlreadyConsumedException.class)
    public ResponseEntity<ErrorResponse> handlePreviewAlreadyConsumed(
            PreviewAlreadyConsumedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(exception.getErrorCode().getStatus())
                .body(ErrorResponse.consumed(
                        exception.getErrorCode(), traceId(request), exception.getScheduleId()));
    }

    /**
     * 본문 JSON이 깨졌거나 비어 있을 때. 예전에는 핸들러가 없어 Spring 기본 응답이 나가
     * code와 traceId가 없는 본문을 클라이언트가 받았다.
     *
     * <p>열거형에 없는 값을 보낸 경우처럼 어느 필드가 잘못됐는지 알 수 있으면 그 필드를
     * 짚어 준다. 본문 전체를 "읽을 수 없다"고만 하면 클라이언트가 원인을 찾기 어렵다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        ErrorCode.MALFORMED_REQUEST,
                        List.of(unreadableBodyFieldError(exception)),
                        traceId(request)));
    }

    private ErrorResponse.FieldErrorResponse unreadableBodyFieldError(
            HttpMessageNotReadableException exception) {
        InvalidFormatException invalidFormat = findInvalidFormat(exception);
        if (invalidFormat != null) {
            String field = invalidFormat.getPath().stream()
                    .map(Reference::getPropertyName)
                    .filter(Objects::nonNull)
                    .reduce((parent, child) -> parent + "." + child)
                    .orElse("body");
            return new ErrorResponse.FieldErrorResponse(field,
                    "값 \"%s\" 을(를) 쓸 수 없습니다.%s"
                            .formatted(invalidFormat.getValue(), allowedValues(invalidFormat)));
        }
        return new ErrorResponse.FieldErrorResponse(
                "body", "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요.");
    }

    /** 레코드 생성 중 실패하면 원인이 한 겹 더 안쪽에 들어가므로 사슬을 따라 내려간다. */
    private InvalidFormatException findInvalidFormat(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalidFormat) {
                return invalidFormat;
            }
        }
        return null;
    }

    /** 열거형이면 쓸 수 있는 값을 알려준다. 그 밖의 타입은 알려줄 목록이 없다. */
    private String allowedValues(InvalidFormatException exception) {
        Class<?> targetType = exception.getTargetType();
        if (targetType == null || !targetType.isEnum()) {
            return "";
        }
        return Arrays.stream(targetType.getEnumConstants())
                .map(String::valueOf)
                .collect(Collectors.joining(", ", " 가능한 값: ", ""));
    }

    /** 경로 변수나 쿼리 파라미터의 타입이 맞지 않을 때. 예: /schedules/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String required = exception.getRequiredType() == null
                ? "올바른 형식" : exception.getRequiredType().getSimpleName();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        validationErrorCode(request),
                        List.of(new ErrorResponse.FieldErrorResponse(
                                exception.getName(),
                                "%s 형식이 아닙니다. 요청 값: %s".formatted(required, exception.getValue()))),
                        traceId(request)));
    }

    /** 필수 쿼리 파라미터가 없을 때. 예: /locations/search 에 keyword 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        validationErrorCode(request),
                        List.of(new ErrorResponse.FieldErrorResponse(
                                exception.getParameterName(), "필수 값입니다.")),
                        traceId(request)));
    }

    /** 필수 헤더가 없을 때. 예: Idempotency-Key */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        validationErrorCode(request),
                        List.of(new ErrorResponse.FieldErrorResponse(
                                exception.getHeaderName(), "필수 헤더입니다.")),
                        traceId(request)));
    }

    /**
     * multipart 요청에서 파일 파트가 통째로 빠졌을 때. 그대로 두면 500 이 나가 프론트가
     * 서버 장애로 오해한다.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        validationErrorCode(request),
                        List.of(new ErrorResponse.FieldErrorResponse(
                                exception.getRequestPartName(), "필수 파트입니다.")),
                        traceId(request)));
    }

    /**
     * 서블릿이 크기를 먼저 막았을 때. MediaService 의 검사는 파일이 컨트롤러까지 와야
     * 돌기 때문에, 그보다 큰 요청은 여기로 떨어진다. 두 경로가 같은 코드를 주어야
     * 프론트가 한 가지 분기만 두면 된다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        // 이 예외는 어느 파트가 컸는지 알려주지 않는다. 필드명을 지어내면 다른 multipart
        // 경로가 생겼을 때 있지도 않은 필드를 가리키게 되므로 코드와 메시지로만 알린다.
        return ResponseEntity
                .status(ErrorCode.MEDIA_FILE_TOO_LARGE.getStatus())
                .body(ErrorResponse.of(ErrorCode.MEDIA_FILE_TOO_LARGE, List.of(), traceId(request)));
    }

    /** 존재하지 않는 경로. 오류 응답 형태를 나머지와 맞춘다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, List.of(), traceId(request)));
    }

    /**
     * 위에서 다루지 않은 모든 예외. traceId 없이 나가면 응답과 로그를 연결할 수 없어
     * 원인 추적이 불가능했다. 내부 메시지는 응답에 담지 않고 로그에만 남긴다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        String traceId = traceId(request);
        log.error("unhandled exception. traceId={}, method={}, uri={}",
                traceId, request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, List.of(), traceId));
    }

    private ErrorResponse.FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return new ErrorResponse.FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }

    /**
     * 요청 경로로 도메인을 골라 오류 코드를 정한다. 예전에는 장소 검색 실패에도
     * INVALID_SCHEDULE_CONDITION("일정 조건이 올바르지 않습니다")이 나갔다.
     */
    private ErrorCode validationErrorCode(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/schedule-previews")) {
            return ErrorCode.INVALID_SCHEDULE_PREVIEW_REQUEST;
        }
        if (uri.startsWith("/api/v1/places") || uri.startsWith("/api/v1/locations")) {
            return ErrorCode.INVALID_PLACE_SEARCH_REQUEST;
        }
        // 댓글 경로가 /api/v1/posts/{postId}/comments 라 게시물보다 먼저 본다.
        if (uri.startsWith("/api/v1/posts") && uri.contains("/comments")) {
            return ErrorCode.INVALID_COMMENT_REQUEST;
        }
        if (uri.startsWith("/api/v1/posts")) {
            return ErrorCode.INVALID_POST_REQUEST;
        }
        if (uri.startsWith("/api/v1/users")) {
            return ErrorCode.INVALID_USER_REQUEST;
        }
        if (uri.startsWith("/api/v1/media")) {
            return ErrorCode.INVALID_MEDIA_FILE;
        }
        // 분기가 없으면 로그인 요청 오류가 "일정 조건이 올바르지 않습니다"로 나간다.
        if (uri.startsWith("/api/v1/auth")) {
            return ErrorCode.INVALID_TOKEN;
        }
        return ErrorCode.INVALID_SCHEDULE_CONDITION;
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
        return traceId == null ? "" : traceId.toString();
    }
}
