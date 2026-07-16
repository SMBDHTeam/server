package com.server.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SchedulePreviewResponse(
        UUID previewId,
        String status,
        boolean canGenerate,
        OffsetDateTime expiresAt,
        String timeZone,
        String lodgingMode,
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
