package com.server.auth.service;

import com.server.user.domain.UserRole;

/** 액세스 토큰이 가리키는 사용자. DB 조회 없이 토큰만으로 만든다. */
public record AuthenticatedUser(Long id, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
