package com.server.share.domain;

import com.server.schedule.domain.Schedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "share_links",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_share_links_token_hash",
                columnNames = "token_hash"
        )
)
public class ShareLink {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ShareLink() {
    }

    public ShareLink(Schedule schedule, String tokenHash, LocalDateTime expiresAt) {
        this.id = UUID.randomUUID();
        this.schedule = schedule;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public void revoke(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isAvailable(LocalDateTime now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public UUID getId() {
        return id;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
