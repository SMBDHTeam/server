package com.server.external.openai;

import java.util.List;

public interface AiPlanningPromptClient {

    Interpretation interpret(String prompt);

    record Interpretation(
            List<String> preferences,
            List<String> unrecognizedTexts,
            int confidence
    ) {
    }
}
