package com.server.post.dto;

/**
 * 최상위 댓글을 감춘 이유. 지워지거나 작성자가 탈퇴하면 목록에서 빼지 않고 작성자와
 * 내용만 비운다. 빼버리면 달려 있던 답글이 부모를 잃고 함께 사라진다.
 * "삭제된 댓글입니다" 같은 문구는 이 값을 보고 클라이언트가 정한다.
 */
public enum CommentHiddenReason {

    /** 작성자가 지웠다. */
    DELETED,

    /** 작성자가 탈퇴했다. */
    WITHDRAWN
}
