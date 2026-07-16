package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleBasedPlanningPromptInterpreterTest {

    private final RuleBasedPlanningPromptInterpreter interpreter =
            new RuleBasedPlanningPromptInterpreter();

    @Test
    void interpretsSupportedSoftPreferences() {
        var result = interpreter.interpret("바다를 보고 맛집도 가되 걷는 시간은 적었으면 좋겠어요");

        assertThat(result.preferences())
                .containsExactly("LOW_WALKING", "PREFER_SEA_VIEW", "PREFER_FOOD");
        assertThat(result.unrecognizedTexts()).isEmpty();
        assertThat(result.source()).isEqualTo("RULE_BASED");
        assertThat(result.confidence()).isEqualTo(100);
    }

    @Test
    void preservesUnsupportedPromptForUserReview() {
        var result = interpreter.interpret("비 오는 날 사진이 잘 나오는 조용한 곳");

        assertThat(result.preferences()).isEmpty();
        assertThat(result.unrecognizedTexts())
                .containsExactly("비 오는 날 사진이 잘 나오는 조용한 곳");
    }
}
