package com.server.user.domain;

/**
 * 사용자 권한.
 *
 * <p>Spring Security 의 {@code hasRole("ADMIN")} 은 권한 문자열에 {@code ROLE_} 접두사를
 * 요구한다. 접두사는 {@link #authority()} 에서만 붙이고 DB 와 토큰에는 이름만 저장한다.
 */
public enum UserRole {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
