package com.server.notification.domain;

/**
 * 알림 종류. 지금은 커뮤니티에서 생기는 것만 있지만 알림 자체는 커뮤니티 전용이 아니다.
 * 일정처럼 다른 도메인에서 알림이 필요해지면 여기에 값을 더하면 되고, 컬럼이 문자열이라
 * 마이그레이션은 필요 없다.
 */
public enum NotificationType {

    /** 내 게시물에 좋아요가 눌렸다. 대상은 게시물이다. */
    POST_LIKE,

    /** 내 게시물에 댓글이 달렸다. 대상은 댓글이다. */
    COMMENT,

    /** 내 댓글에 답글이 달렸다. 대상은 답글이다. */
    COMMENT_REPLY,

    /** 내 댓글에 좋아요가 눌렸다. 대상은 댓글이다. */
    COMMENT_LIKE,

    /** 누군가 나를 팔로우했다. 대상은 그 사람이다. */
    FOLLOW
}
