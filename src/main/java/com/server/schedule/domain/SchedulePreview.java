package com.server.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_previews")
public class SchedulePreview {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "lodging_mode", nullable = false)
    private String lodgingMode;

    @Column(name = "route_coverage", nullable = false)
    private String routeCoverage;

    @Column(name = "input_json", nullable = false, columnDefinition = "text")
    private String inputJson;

    @Column(name = "resolved_days_json", nullable = false, columnDefinition = "text")
    private String resolvedDaysJson;

    @Column(name = "resolved_end_constraint_json", columnDefinition = "text")
    private String resolvedEndConstraintJson;

    @Column(name = "applied_defaults_json", nullable = false, columnDefinition = "text")
    private String appliedDefaultsJson;

    @Column(name = "interpreted_prompt_json", nullable = false, columnDefinition = "text")
    private String interpretedPromptJson;

    @Column(name = "warnings_json", nullable = false, columnDefinition = "text")
    private String warningsJson;

    @Column(name = "conflicts_json", nullable = false, columnDefinition = "text")
    private String conflictsJson;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SchedulePreview() {
    }

    public SchedulePreview(
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String lodgingMode,
            String routeCoverage,
            String inputJson,
            String resolvedDaysJson,
            String resolvedEndConstraintJson,
            String appliedDefaultsJson,
            String interpretedPromptJson,
            String warningsJson,
            String conflictsJson,
            OffsetDateTime expiresAt,
            OffsetDateTime createdAt
    ) {
        this.id = UUID.randomUUID();
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.timeZone = timeZone;
        this.lodgingMode = lodgingMode;
        this.routeCoverage = routeCoverage;
        this.inputJson = inputJson;
        this.resolvedDaysJson = resolvedDaysJson;
        this.resolvedEndConstraintJson = resolvedEndConstraintJson;
        this.appliedDefaultsJson = appliedDefaultsJson;
        this.interpretedPromptJson = interpretedPromptJson;
        this.warningsJson = warningsJson;
        this.conflictsJson = conflictsJson;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void expire() {
        this.status = "EXPIRED";
    }

    public void consume(OffsetDateTime consumedAt) {
        this.status = "CONSUMED";
        this.consumedAt = consumedAt;
    }

    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getTimeZone() { return timeZone; }
    public String getLodgingMode() { return lodgingMode; }
    public String getRouteCoverage() { return routeCoverage; }
    public String getInputJson() { return inputJson; }
    public String getResolvedDaysJson() { return resolvedDaysJson; }
    public String getResolvedEndConstraintJson() { return resolvedEndConstraintJson; }
    public String getAppliedDefaultsJson() { return appliedDefaultsJson; }
    public String getInterpretedPromptJson() { return interpretedPromptJson; }
    public String getWarningsJson() { return warningsJson; }
    public String getConflictsJson() { return conflictsJson; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
}
