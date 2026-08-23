package com.server.follow.domain;

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
 * 팔로우 관계. {@code follower}가 {@code following}을 팔로우한다. 방향이 있으므로
 * 서로 팔로우하려면 두 행이 필요하다.
 */
@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
public class Follow {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Follow() {
    }

    public Follow(User follower, User following) {
        this.follower = follower;
        this.following = following;
        this.createdAt = LocalDateTime.now();
    }

    public User getFollower() {
        return follower;
    }

    public User getFollowing() {
        return following;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
