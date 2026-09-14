package com.server.admin.domain;

/**
 * 관리자가 한 조치의 종류.
 *
 * <p>이름을 그대로 저장한다. 값을 바꾸면 과거 기록의 의미가 바뀌므로 이름은 고정하고,
 * 새 조치는 추가만 한다.
 */
public enum AdminActionType {
    REPORT_STATUS_CHANGED,
    POST_DELETED,
    COMMENT_DELETED,
    USER_SUSPENDED,
    USER_SUSPENSION_RELEASED,
    USER_ROLE_CHANGED,
    PLACE_HIDDEN,
    PLACE_UNHIDDEN,
    INGESTION_RUN
}
