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

    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfiguration {
    }
}
