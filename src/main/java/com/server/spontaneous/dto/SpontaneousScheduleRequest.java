package com.server.spontaneous.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SpontaneousScheduleRequest(
        @NotNull UUID previewId,
        @NotBlank @Size(min = 32, max = 200000) String previewToken
) {
}
