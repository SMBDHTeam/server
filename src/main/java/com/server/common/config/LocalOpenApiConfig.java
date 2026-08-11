package com.server.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class LocalOpenApiConfig {

    @Bean
    OpenAPI tourServerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Tour Server API")
                .version("v1")
                .description("""
                        부산 여행 일정 서버 API 문서입니다. 코드에서 생성되므로 배포된 서버의 실제 계약과 항상 일치합니다.

                        일정 생성·조회는 FastAPI에 위임되며, 위임이 꺼져 있으면 503 EXTERNAL_PROVIDER_UNAVAILABLE을 반환합니다.
                        일정 생성 예제의 placeId는 해당 환경에 실제로 적재된 장소로 채워집니다."""));
    }
}
