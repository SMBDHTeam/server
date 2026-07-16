package com.server.schedule.planner;

import com.server.schedule.dto.SchedulePreviewResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedPlanningPromptInterpreter implements PlanningPromptInterpreter {

    @Override
    public SchedulePreviewResponse.InterpretedPrompt interpret(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return new SchedulePreviewResponse.InterpretedPrompt(List.of(), List.of());
        }

        List<String> preferences = new ArrayList<>();
        String normalized = prompt.toLowerCase();
        if (normalized.contains("걷") && (normalized.contains("적") || normalized.contains("싫"))) {
            preferences.add("LOW_WALKING");
        }
        if (normalized.contains("바다") || normalized.contains("해변")) {
            preferences.add("PREFER_SEA_VIEW");
        }
        if (normalized.contains("맛집") || normalized.contains("음식")) {
            preferences.add("PREFER_FOOD");
        }

        List<String> unrecognized = preferences.isEmpty() ? List.of(prompt.trim()) : List.of();
        return new SchedulePreviewResponse.InterpretedPrompt(List.copyOf(preferences), unrecognized);
    }
}
