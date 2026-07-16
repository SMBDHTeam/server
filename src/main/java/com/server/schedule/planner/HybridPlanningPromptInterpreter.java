package com.server.schedule.planner;

import com.server.external.openai.AiPlanningPromptClient;
import com.server.schedule.dto.SchedulePreviewResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(prefix = "app.ai-planner", name = "enabled", havingValue = "true")
public class HybridPlanningPromptInterpreter implements PlanningPromptInterpreter {

    private final AiPlanningPromptClient aiClient;
    private final RuleBasedPlanningPromptInterpreter fallback;

    public HybridPlanningPromptInterpreter(
            AiPlanningPromptClient aiClient,
            RuleBasedPlanningPromptInterpreter fallback
    ) {
        this.aiClient = aiClient;
        this.fallback = fallback;
    }

    @Override
    public SchedulePreviewResponse.InterpretedPrompt interpret(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return fallback.interpret(prompt);
        }
        try {
            AiPlanningPromptClient.Interpretation interpretation = aiClient.interpret(prompt);
            return new SchedulePreviewResponse.InterpretedPrompt(
                    interpretation.preferences(),
                    interpretation.unrecognizedTexts(),
                    "HYBRID_AI",
                    interpretation.confidence()
            );
        } catch (RuntimeException exception) {
            SchedulePreviewResponse.InterpretedPrompt result = fallback.interpret(prompt);
            return new SchedulePreviewResponse.InterpretedPrompt(
                    result.preferences(),
                    result.unrecognizedTexts(),
                    "FALLBACK",
                    result.confidence()
            );
        }
    }
}
