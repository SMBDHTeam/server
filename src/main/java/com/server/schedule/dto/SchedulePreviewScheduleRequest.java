package com.server.schedule.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SchedulePreviewScheduleRequest(@NotNull UUID previewId) {
}
