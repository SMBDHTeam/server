package com.server.post.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link PostLike}의 복합 기본키. 필드 이름은 엔티티의 {@code @Id} 필드와 같아야 하고,
 * 타입은 대상 엔티티의 기본키 타입을 쓴다.
 */
public class PostLikeId implements Serializable {

    private Long post;
    private Long user;

    protected PostLikeId() {
    }

    public PostLikeId(Long post, Long user) {
        this.post = post;
        this.user = user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PostLikeId that)) {
            return false;
        }
        return Objects.equals(post, that.post) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(post, user);
    }
}
