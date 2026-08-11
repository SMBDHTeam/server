package com.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchedulePreviewResponse(
        @Schema(description = "일정 생성 요청에 그대로 전달한다.",
                example = "6afa151a-3fb2-4f3d-821f-53228b88d1c0")
        UUID previewId,
        @Schema(description = "READY 면 그대로 생성할 수 있고, REQUIRES_ACTION 이면 conflicts 를 해결해야 한다.",
                example = "READY", allowableValues = {"READY", "REQUIRES_ACTION", "CONSUMED", "EXPIRED"})
        String status,
        @Schema(description = "이 Preview 로 바로 일정을 만들 수 있는지", example = "true")
        boolean canGenerate,
        @Schema(description = "만료 시각. 생성 후 30분", example = "2026-09-20T01:30:00Z")
        OffsetDateTime expiresAt,
        @Schema(example = "Asia/Seoul") String timeZone,
        @Schema(example = "UNDECIDED", allowableValues = {"UNDECIDED", "FIXED", "PER_NIGHT"})
        String lodgingMode,
        @Schema(description = "실제 경로를 어디까지 계산했는지", example = "ATTRACTION_ROUTES_ONLY")
        String routeCoverage,
        List<ResolvedDay> resolvedDays,
        ResolvedEndConstraint resolvedEndConstraint,
        List<AppliedDefault> appliedDefaults,
        InterpretedPrompt interpretedPrompt,
        List<Warning> warnings,
        List<Conflict> conflicts,
        UUID scheduleId
) {
    public record Location(
            String name,
            String address,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
    }

    public record ResolvedDay(
            LocalDate date,
            LocalTime availableFrom,
            LocalTime availableUntil,
            Location startLocation,
            Location endLocation,
            String startLocationSource,
            String endLocationSource
    ) {
    }

    public record ResolvedEndConstraint(
            String type,
            String targetAt,
            int appliedBufferMinutes,
            LocalTime availableUntil
    ) {
    }

    public record AppliedDefault(String fieldPath, Object resolvedValue, String reasonCode) {
    }

    public record InterpretedPrompt(
            List<String> preferences,
            List<String> unrecognizedTexts,
            String source,
            int confidence
    ) {
        public InterpretedPrompt(List<String> preferences, List<String> unrecognizedTexts) {
            this(preferences, unrecognizedTexts, "RULE_BASED", 100);
        }
    }

    public record Warning(String code, LocalDate date, String message) {
    }

    public record Conflict(
            String code,
            String message,
            String fieldPath,
            LocalDate conflictDate,
            Integer requiredMinutes,
            Integer availableMinutes,
            List<String> adjustableFields
    ) {
    }
}
