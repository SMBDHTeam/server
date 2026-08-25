package com.server.notification.domain;

import com.server.user.domain.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "recipient_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private User recipient;

    /** 행동한 사람. 시스템이 보내는 알림에는 없다. */
    @JoinColumn(name = "actor_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private NotificationTargetType targetType;

    /** 대상이 종류마다 달라 외래키를 걸지 않는다. 대상이 지워졌을 수 있다. */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(
            User recipient,
            User actor,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId
    ) {
        this.recipient = recipient;
        this.actor = actor;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public User getActor() {
        return actor;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRecipient(Long userId) {
        return recipient.getId().equals(userId);
    }

    /** 이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다. */
    public void markAsRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
