package com.server.user.domain;

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
 * 닉네임 유일성은 탈퇴하지 않은 사용자에게만 적용하므로 JPA 제약 대신
 * migration의 부분 고유 인덱스(uk_users_nickname_active)로 관리한다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "profile_image_url", columnDefinition = "text")
    private String profileImageUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private String email;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    /** 제공자가 준 고유 식별자. 구글은 {@code sub} 다. 이메일은 바뀌므로 쓰지 않는다. */
    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "suspended_reason", columnDefinition = "text")
    private String suspendedReason;

    protected User() {
    }

    public User(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = LocalDateTime.now();
    }

    /** 소셜 로그인으로 처음 들어온 사용자. */
    public static User ofOAuth(
            AuthProvider provider,
            String providerId,
            String email,
            String nickname,
            String profileImageUrl,
            UserRole role
    ) {
        User user = new User(nickname, profileImageUrl);
        user.provider = provider;
        user.providerId = providerId;
        user.email = email;
        user.role = role == null ? UserRole.USER : role;
        return user;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void removeProfileImage() {
        this.profileImageUrl = null;
    }

    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.status = UserStatus.WITHDRAWN;
    }

    public String getEmail() {
        return email;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getSuspendedUntil() {
        return suspendedUntil;
    }

    public String getSuspendedReason() {
        return suspendedReason;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /**
     * 로그인할 때마다 제공자가 준 최신 정보로 맞춘다. 닉네임은 사용자가 직접 바꿀 수 있으므로
     * 건드리지 않는다. 구글에서 이름을 바꿨다고 우리 서비스의 닉네임이 되돌아가면 안 된다.
     */
    public void syncFromProvider(String email, String profileImageUrl) {
        this.email = email;
        if (this.profileImageUrl == null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }

    public void suspend(LocalDateTime until, String reason) {
        this.status = UserStatus.SUSPENDED;
        this.suspendedUntil = until;
        this.suspendedReason = reason;
    }

    public void releaseSuspension() {
        this.status = UserStatus.ACTIVE;
        this.suspendedUntil = null;
        this.suspendedReason = null;
    }

    /**
     * 지금 쓰기가 막혀 있는지. 기간이 지난 정지는 스스로 풀린 것으로 본다.
     * 별도 배치 없이 만료를 처리하기 위함이다.
     */
    public boolean isWriteBlockedAt(LocalDateTime now) {
        if (status == UserStatus.WITHDRAWN) {
            return true;
        }
        if (status != UserStatus.SUSPENDED) {
            return false;
        }
        return suspendedUntil == null || suspendedUntil.isAfter(now);
    }
}
