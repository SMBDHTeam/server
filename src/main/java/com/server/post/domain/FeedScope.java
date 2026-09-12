package com.server.post.domain;

/**
 * 피드에 누구의 글을 담을지.
 *
 * <p>문자열로 받던 값이다. {@code "following"} 인지만 보고 나머지는 조용히 전체 피드로
 * 넘겼기 때문에, 오타를 내도 글이 그럭저럭 나와 잘못 부른 줄 모르는 일이 있었다.
 * 열거형으로 받으면 모르는 값이 400 으로 돌아오고 Swagger 에 고를 수 있는 값이 뜬다.
 */
public enum FeedScope {

    /** 차단하지 않은 모든 사람의 글. 값을 보내지 않으면 이것이다. */
    ALL,

    /** 요청자가 팔로우한 사람들의 글만. 로그인이 필요하다. */
    FOLLOWING;

    public boolean isFollowing() {
        return this == FOLLOWING;
    }
}
