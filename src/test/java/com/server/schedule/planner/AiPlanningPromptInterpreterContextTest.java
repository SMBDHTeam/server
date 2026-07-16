package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "app.ai-planner.enabled=true")
@ActiveProfiles("test")
class AiPlanningPromptInterpreterContextTest {

    @Autowired
    private PlanningPromptInterpreter interpreter;

    @Test
    void selectsHybridInterpreterAndFallsBackWithoutApiKey() {
        assertThat(interpreter).isInstanceOf(HybridPlanningPromptInterpreter.class);

        var result = interpreter.interpret("바다를 보고 싶어요");

        assertThat(result.preferences()).containsExactly("PREFER_SEA_VIEW");
        assertThat(result.source()).isEqualTo("FALLBACK");
    }
}
