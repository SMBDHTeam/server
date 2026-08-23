package com.server.block.domain;

import com.server.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 차단 관계. {@code blocker}가 {@code blocked}를 차단한다. 방향이 있으며,
 * 차단한 사람의 피드에서 상대 게시물이 사라진다.
 */
@Entity
@Table(name = "blocks")
@IdClass(BlockId.class)
public class Block {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Block() {
    }

    public Block(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = LocalDateTime.now();
    }

    public User getBlocker() {
        return blocker;
    }

    public User getBlocked() {
        return blocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
