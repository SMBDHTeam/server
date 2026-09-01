package com.server.common.config;

import com.server.common.error.ErrorCode;
import com.server.common.error.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모든 오퍼레이션에 공통 오류 응답을 붙인다.
 *
 * <p>서버는 어떤 실패에서도 {@link ErrorResponse} 형태를 지키지만, 컨트롤러에
 * {@code @ApiResponse}가 하나도 없어 OpenAPI 문서에는 오류 응답이 전혀 나타나지
 * 않았다. 클라이언트가 {@code code}로 분기하려면 어떤 코드가 오는지 문서에서
 * 볼 수 있어야 한다.
 *
 * <p>메서드마다 애노테이션을 붙이지 않고 여기서 일괄 적용한다. 엔드포인트가
 * 늘어나도 문서가 자동으로 따라온다.
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class ErrorResponseOpenApiConfig {

    private static final String ERROR_SCHEMA_NAME = "ErrorResponse";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA_NAME;

    @Bean
    OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            registerErrorSchemas(openApi);
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((httpMethod, operation) ->
                            applyErrorResponses(
                                    path, httpMethod.name().toLowerCase(), operation)));
        };
    }

    /** {@link ErrorResponse}와 중첩 타입을 components.schemas에 등록한다. */
    private void registerErrorSchemas(io.swagger.v3.oas.models.OpenAPI openApi) {
        Map<String, Schema> schemas = ModelConverters.getInstance().readAll(ErrorResponse.class);
        schemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
    }

    private void applyErrorResponses(String path, String httpMethod, Operation operation) {
        ApiResponses responses = operation.getResponses();

        // 파라미터도 본문도 없는 오퍼레이션은 요청 값으로 실패할 여지가 없다.
        boolean acceptsInput = (operation.getParameters() != null && !operation.getParameters().isEmpty())
                || operation.getRequestBody() != null;
        if (acceptsInput) {
            putIfAbsent(responses, "400", "요청 값이 올바르지 않다.", codesOf(400));
        }
        if (path.contains("{")) {
            putIfAbsent(responses, "404", "대상을 찾을 수 없다.", codesOf(404));
        }
        // 멱등성 충돌과 Preview 만료는 Preview를 만들거나 소비하는 경로에서만 나온다.
        if (consumesPreview(path, httpMethod)) {
            putIfAbsent(responses, "409", "같은 멱등성 키 재사용, 이미 소비된 Preview, "
                    + "또는 같은 요청의 생성이 진행 중이다.", codesOf(409));
            putIfAbsent(responses, "410", "Preview가 만료됐다.", codesOf(410));
        }
        // 인증이 필요한 경로. 지금은 관리자만이며, 인가를 전면 적용하면 대상이 늘어난다.
        if (requiresAuthentication(path, httpMethod)) {
            putIfAbsent(responses, "401", "인증이 필요하거나 토큰이 유효하지 않다.", codesOf(401));
            putIfAbsent(responses, "403", "권한이 없다.", codesOf(403));
        }
        if (delegatesToFastApi(path, httpMethod)) {
            putIfAbsent(responses, "503",
                    "일정 생성·조회를 담당하는 FastAPI가 응답하지 않는다.", codesOf(503));
        }
        if (uploadsToStorage(path)) {
            putIfAbsent(responses, "413", "파일이 한 건당 허용 크기를 넘었다.", codesOf(413));
            putIfAbsent(responses, "415", "지원하지 않는 형식이거나 내용이 확장자와 다르다.",
                    codesOf(415));
            putIfAbsent(responses, "503",
                    "파일 저장소가 꺼져 있거나 응답하지 않는다.", codesOf(503));
        }
        putIfAbsent(responses, "500",
                "서버가 처리하지 못한 예외. 응답에 내부 메시지를 담지 않으며 "
                        + "원인은 같은 traceId로 서버 로그에 남는다.", codesOf(500));
    }

    /**
     * 인증이 필요한 경로.
     *
     * <p>관리자 경로와 커뮤니티가 여기 해당한다. 일정 API 는 인증 없이 만든 일정이 남아
     * 있어 아직 걸지 않았고, 걸 때 여기에 더한다.
     */
    private boolean requiresAuthentication(String path, String httpMethod) {
        if (path.startsWith("/api/v1/admin")) {
            return true;
        }
        return isCommunity(path) && !isPubliclyReadable(path, httpMethod);
    }

    /** 커뮤니티 경로. SecurityConfig 의 인가 규칙과 같은 범위를 본다. */
    private boolean isCommunity(String path) {
        return path.startsWith("/api/v1/posts")
                || path.startsWith("/api/v1/users")
                || path.startsWith("/api/v1/notifications")
                || path.startsWith("/api/v1/reports")
                || path.startsWith("/api/v1/media");
    }

    /**
     * 로그인 없이 볼 수 있는 경로.
     *
     * <p>공유 링크로 들어온 사람이 그 글과 댓글까지는 봐야 한다. 토큰을 보내면 읽으므로
     * {@code liked} 같은 값은 로그인해야 채워진다.
     */
    private boolean isPubliclyReadable(String path, String httpMethod) {
        if (!"get".equals(httpMethod)) {
            // 같은 경로라도 댓글 작성은 로그인이 필요하다.
            return false;
        }
        return path.equals("/api/v1/posts/{postId}")
                || path.equals("/api/v1/posts/{postId}/comments");
    }

    /** Preview를 만들거나 소비하는 경로. */
    private boolean consumesPreview(String path, String httpMethod) {
        if (path.startsWith("/api/v1/schedule-previews")) {
            return true;
        }
        return "post".equals(httpMethod) && "/api/v1/schedules".equals(path);
    }

    /**
     * FastAPI 위임을 타는 경로.
     *
     * <p>공유 링크 발급·폐기는 Spring이 DB를 직접 다루므로 위임하지 않지만,
     * 공유된 일정과 지도 조회는 {@code ScheduleService}를 거쳐 위임을 탄다.
     */
    private boolean delegatesToFastApi(String path, String httpMethod) {
        if (path.startsWith("/api/v1/schedule-previews")) {
            return true;
        }
        if (path.startsWith("/api/v1/shared-schedules")) {
            return true;
        }
        if (path.startsWith("/api/v1/schedules")) {
            // /schedules/{id}/shares 계열은 공유 링크만 다루고 위임하지 않는다.
            return !path.contains("/shares");
        }
        return false;
    }

    /** 외부 파일 저장소(S3)를 타는 경로. 저장소가 꺼져 있으면 503 을 준다. */
    private boolean uploadsToStorage(String path) {
        return path.startsWith("/api/v1/media");
    }

    private void putIfAbsent(ApiResponses responses, String status, String summary, String codes) {
        if (responses.containsKey(status)) {
            return;
        }
        String description = codes.isBlank() ? summary : summary + " 가능한 code: " + codes;
        responses.addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)))));
    }

    /** 해당 HTTP 상태로 나갈 수 있는 ErrorCode 이름을 모은다. */
    private String codesOf(int status) {
        return Arrays.stream(ErrorCode.values())
                .filter(code -> code.getStatus().value() == status)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

}
