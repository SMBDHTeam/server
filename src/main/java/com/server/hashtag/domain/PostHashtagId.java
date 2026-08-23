package com.server.hashtag.domain;

import java.io.Serializable;
import java.util.Objects;

/** {@link PostHashtag}의 복합 기본키. 같은 게시물에 같은 태그가 두 번 붙지 않는다. */
public class PostHashtagId implements Serializable {

    private Long post;
    private Long hashtag;

    protected PostHashtagId() {
    }

    public PostHashtagId(Long post, Long hashtag) {
        this.post = post;
        this.hashtag = hashtag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PostHashtagId that)) {
            return false;
        }
        return Objects.equals(post, that.post) && Objects.equals(hashtag, that.hashtag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(post, hashtag);
    }
}
