package com.server.post.domain;

/**
 * 게시물에 첨부할 수 있는 미디어 종류. 문자열로 받으면 오타나 임의의 값이 그대로
 * 저장되므로 열거형으로 고정한다. 값이 늘어나면 여기와 저장소 정책을 함께 본다.
 */
public enum MediaType {
    IMAGE,
    VIDEO
}
