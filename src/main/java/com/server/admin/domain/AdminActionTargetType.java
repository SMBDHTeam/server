package com.server.admin.domain;

/** 조치 대상. {@code SYSTEM} 은 특정 대상이 없는 조치(적재 실행 등)에 쓴다. */
public enum AdminActionTargetType {
    REPORT,
    POST,
    COMMENT,
    USER,
    PLACE,
    SYSTEM
}
