package com.server.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 설정.
 *
 * <p>허용 헤더와 노출 헤더는 기본값을 코드에 둔다. 설정이 비어 있으면 기본값을 쓰므로
 * 환경변수를 잘못 비워도 브라우저에서 API 전체가 막히지 않는다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedHeaders,
        List<String> exposedHeaders
) {

    /**
     * 브라우저가 보낼 수 있는 요청 헤더.
     *
     * <p>{@code Authorization}이 없으면 로그인한 요청이 preflight에서 막힌다.
     */
    private static final List<String> DEFAULT_ALLOWED_HEADERS = List.of(
            "Content-Type",
            "Authorization",
            "X-Trace-Id",
            "Idempotency-Key");

    /**
     * 브라우저가 응답에서 읽을 수 있는 헤더.
     *
     * <p>{@code TraceIdFilter}가 모든 응답에 {@code X-Trace-Id}를 넣고 오류 응답의
     * {@code traceId}와 같은 값이라고 문서화하지만, 노출 헤더로 지정하지 않으면
     * 다른 오리진의 스크립트는 이 값을 읽을 수 없다.
     */
    private static final List<String> DEFAULT_EXPOSED_HEADERS = List.of("X-Trace-Id");

    public CorsProperties {
        allowedOrigins = clean(allowedOrigins, List.of());
        allowedHeaders = clean(allowedHeaders, DEFAULT_ALLOWED_HEADERS);
        exposedHeaders = clean(exposedHeaders, DEFAULT_EXPOSED_HEADERS);
    }

    private static List<String> clean(List<String> values, List<String> fallback) {
        if (values == null) {
            return fallback;
        }
        List<String> cleaned = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
