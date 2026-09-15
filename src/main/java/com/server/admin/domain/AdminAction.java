package com.server.admin.domain;

import com.server.common.support.ServerClock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 관리자 조치 한 건.
 *
 * <p>지우거나 고치지 않는다. 기록을 나중에 손댈 수 있으면 기록이 아니다.
 * 조치를 되돌리는 경우에도 원래 기록을 남기고 되돌린 사실을 새 행으로 적는다.
 */
@Entity
@Table(name = "admin_actions")
public class AdminAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminActionType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AdminActionTargetType targetType;

    /** 대상이 없는 조치에서는 비어 있다. */
    @Column(name = "target_id")
    private Long targetId;

    /** 관리자가 입력한 사유. 정지·숨김처럼 사유를 받는 조치에만 있다. */
    @Column(length = 500)
    private String reason;

    /** 사유와 별개로 남길 값. 상태 전이나 실행 결과처럼 나중에 되짚을 때 필요한 것. */
    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminAction() {
    }

    public AdminAction(
            Long adminId,
            AdminActionType action,
            AdminActionTargetType targetType,
            Long targetId,
            String reason,
            String detail
    ) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = trim(reason);
        this.detail = trim(detail);
        this.createdAt = ServerClock.now();
    }

    /** 컬럼 한도를 넘겨 기록 자체가 실패하는 것을 막는다. 기록은 조치를 방해하면 안 된다. */
    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    public Long getId() {
        return id;
    }

    public Long getAdminId() {
        return adminId;
    }

    public AdminActionType getAction() {
        return action;
    }

    public AdminActionTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
