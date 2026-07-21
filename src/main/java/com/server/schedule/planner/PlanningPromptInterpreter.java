package com.server.schedule.planner;

import com.server.schedule.dto.SchedulePreviewResponse;

public interface PlanningPromptInterpreter {

    SchedulePreviewResponse.InterpretedPrompt interpret(String prompt);
}
