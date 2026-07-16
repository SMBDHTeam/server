package com.server.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenAiPlanningPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withSystemProperties(
                    "AI_PLANNER_ENABLED=true",
                    "OPENAI_BASE_URL=http://127.0.0.1:19090",
                    "OPENAI_API_KEY=fixture-key"
            );

    @Test
    void bindsAiPlannerRuntimeProperties() {
        contextRunner.run(context -> {
            OpenAiPlanningProperties properties = context.getBean(OpenAiPlanningProperties.class);

            assertThat(properties.enabled()).isTrue();
            assertThat(properties.baseUrl()).isEqualTo("http://127.0.0.1:19090");
            assertThat(properties.apiKey()).isEqualTo("fixture-key");
        });
    }

    @EnableConfigurationProperties(OpenAiPlanningProperties.class)
    static class TestConfiguration {
    }
}
