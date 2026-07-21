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
                .description("로컬 개발용 API 문서입니다. 일정 생성 예제는 로컬 seed 데이터로 바로 실행할 수 있습니다."));
    }
}
