package com.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CorsPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withSystemProperties(
                    "CORS_ALLOWED_ORIGINS=https://frontend.example, https://preview.example"
            );

    @Test
    void bindsAllowedOriginsFromEnvironment() {
        contextRunner.run(context -> assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                .isEqualTo(List.of("https://frontend.example", "https://preview.example")));
    }

    @Test
    void bindsAllowedHeadersFromEnvironment() {
        contextRunner
                .withSystemProperties("CORS_ALLOWED_HEADERS=Content-Type, Authorization")
                .run(context -> assertThat(context.getBean(CorsProperties.class).allowedHeaders())
                        .isEqualTo(List.of("Content-Type", "Authorization")));
    }

    @Test
    void fallsBackToDefaultHeadersWhenConfigurationIsBlank() {
        // 환경변수를 빈 값으로 두면 허용 헤더가 사라져 브라우저에서 API 전체가 막힌다.
        contextRunner
                .withSystemProperties("CORS_ALLOWED_HEADERS=", "CORS_EXPOSED_HEADERS=")
                .run(context -> {
                    CorsProperties properties = context.getBean(CorsProperties.class);
                    assertThat(properties.allowedHeaders())
                            .contains("Content-Type", "Authorization", "X-User-Id");
                    assertThat(properties.exposedHeaders()).contains("X-Trace-Id");
                });
    }

    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfiguration {
    }
}
