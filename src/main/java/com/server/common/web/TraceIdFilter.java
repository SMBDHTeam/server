package com.server.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 추적 ID를 만들어 응답 헤더·오류 본문·로그 MDC에 함께 남긴다.
 *
 * <p>Security 필터 체인보다 먼저 실행해야 한다. 뒤에 두면 인증·인가로 걸러진 요청에는
 * 이 필터가 아예 돌지 않아 401·403 응답에 {@code traceId}가 비고 {@code X-Trace-Id}
 * 헤더도 없으며, 서버 로그에서도 그 요청을 찾을 수 없다. 정작 추적이 가장 필요한 실패다.
 *
 * <p>Security 체인의 기본 순서는 -100 이다. 가장 앞으로 두어 모든 요청을 덮는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        request.setAttribute(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        MDC.put(TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String headerTraceId = request.getHeader(TRACE_ID_HEADER);
        if (headerTraceId == null || headerTraceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return headerTraceId;
    }
}
