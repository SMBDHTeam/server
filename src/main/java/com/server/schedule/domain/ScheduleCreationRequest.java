package com.server.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_creation_requests")
public class ScheduleCreationRequest {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preview_id", nullable = false)
    private SchedulePreview preview;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private String status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_json", columnDefinition = "text")
    private String responseJson;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected ScheduleCreationRequest() {
    }

    public ScheduleCreationRequest(
            String idempotencyKey,
            SchedulePreview preview,
            String requestHash,
            OffsetDateTime createdAt
    ) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.preview = preview;
        this.requestHash = requestHash;
        this.status = "IN_PROGRESS";
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plusHours(24);
    }

    public void retry() {
        this.status = "IN_PROGRESS";
        this.lastErrorCode = null;
    }

    public void complete(
            Schedule schedule,
            int responseStatus,
            String responseJson,
            OffsetDateTime completedAt
    ) {
        this.status = "COMPLETED";
        this.schedule = schedule;
        this.responseStatus = responseStatus;
        this.responseJson = responseJson;
        this.completedAt = completedAt;
        this.lastErrorCode = null;
    }

    public void fail(String errorCode) {
        this.status = "FAILED";
        this.lastErrorCode = errorCode;
    }

    public String getRequestHash() { return requestHash; }
    public String getStatus() { return status; }
    public Schedule getSchedule() { return schedule; }
    public SchedulePreview getPreview() { return preview; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseJson() { return responseJson; }
}
