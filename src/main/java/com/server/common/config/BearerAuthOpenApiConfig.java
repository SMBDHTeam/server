package com.server.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 에서 토큰을 넣을 수 있게 한다.
 *
 * <p>보안 스키마가 없으면 Authorize 버튼이 나타나지 않아, 인증이 필요한 API 를 문서에서
 * 시험해 볼 방법이 없다.
 *
 * <p>스키마를 등록만 하고 전역 요구사항으로 걸지는 않는다. 비로그인으로도 되는 API 가
 * 대부분이라, 전역으로 걸면 문서가 실제와 어긋난다. 인증이 필요한 오퍼레이션에만 붙인다.
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class BearerAuthOpenApiConfig {

    static final String SCHEME_NAME = "bearerAuth";

    @Bean
    OpenApiCustomizer bearerAuthCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes(SCHEME_NAME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("POST /api/v1/auth/google 로 받은 accessToken 을 넣는다. "
                            + "Swagger UI 는 Authorize 에 값만 넣으면 Bearer 접두사를 붙여 보낸다."));

            // 인가가 걸린 경로에만 요구사항을 표시한다.
            openApi.getPaths().forEach((path, pathItem) -> {
                if (!path.startsWith("/api/v1/admin")) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> operation.addSecurityItem(
                        new io.swagger.v3.oas.models.security.SecurityRequirement()
                                .addList(SCHEME_NAME)));
            });
        };
    }
}
