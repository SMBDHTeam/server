package com.server.hashtag.dto;

/** 게시물에 붙은 카테고리 이름 한 건. 목록 응답에 태그를 채울 때 쓴다. */
public record PostHashtagNameView(Long postId, String name) {
}
