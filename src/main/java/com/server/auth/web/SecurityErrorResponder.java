package com.server.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.ErrorCode;
import com.server.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 인증·인가 실패를 다른 오류와 같은 형태로 내보낸다.
 *
 * <p>Spring Security 의 401·403 은 필터 단계에서 만들어져 {@code @ControllerAdvice} 가
 * 잡지 못한다. 그대로 두면 이 둘만 {@code code}·{@code fieldErrors}·{@code traceId} 가 없는
 * Spring 기본 응답으로 나가, 클라이언트가 코드로 분기하던 흐름이 401 에서만 깨진다.
 *
 * <p>{@code traceId} 는 {@code TraceIdFilter} 가 요청 속성에 넣어 둔 값을 그대로 쓴다.
 * 응답 헤더 {@code X-Trace-Id} 와 같은 값이어야 로그와 이어진다.
 */
@Component
public class SecurityErrorResponder {

    private static final String TRACE_ID_ATTRIBUTE = "traceId";

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse body = ErrorResponse.of(errorCode, traceId(request));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TRACE_ID_ATTRIBUTE);
        return traceId == null ? "" : traceId.toString();
    }
}
