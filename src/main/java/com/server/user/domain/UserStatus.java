package com.server.user.domain;

/** 계정 상태. 정지는 쓰기만 막고 읽기는 허용한다. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN
}
