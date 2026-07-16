package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.external.openai.AiPlanningPromptClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridPlanningPromptInterpreterTest {

    private final RuleBasedPlanningPromptInterpreter fallback =
            new RuleBasedPlanningPromptInterpreter();

    @Test
    void returnsStructuredAiInterpretation() {
        AiPlanningPromptClient client = prompt -> new AiPlanningPromptClient.Interpretation(
                List.of("PREFER_SEA_VIEW"), List.of("비 오는 날"), 88);
        HybridPlanningPromptInterpreter interpreter =
                new HybridPlanningPromptInterpreter(client, fallback);

        var result = interpreter.interpret("바다를 보고 비 오는 날에도 갈 수 있는 곳");

        assertThat(result.preferences()).containsExactly("PREFER_SEA_VIEW");
        assertThat(result.unrecognizedTexts()).containsExactly("비 오는 날");
        assertThat(result.source()).isEqualTo("HYBRID_AI");
        assertThat(result.confidence()).isEqualTo(88);
    }

    @Test
    void fallsBackToRulesWhenAiCallFails() {
        AiPlanningPromptClient client = prompt -> {
            throw new IllegalStateException("provider unavailable");
        };
        HybridPlanningPromptInterpreter interpreter =
                new HybridPlanningPromptInterpreter(client, fallback);

        var result = interpreter.interpret("걷는 시간은 적고 맛집을 가고 싶어요");

        assertThat(result.preferences()).containsExactly("LOW_WALKING", "PREFER_FOOD");
        assertThat(result.source()).isEqualTo("FALLBACK");
        assertThat(result.confidence()).isEqualTo(100);
    }
}
