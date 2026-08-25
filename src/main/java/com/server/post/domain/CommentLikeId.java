package com.server.post.domain;

import java.io.Serializable;
import java.util.Objects;

/** {@link CommentLike}의 복합 기본키. 같은 댓글에 두 번 좋아요를 누를 수 없다. */
public class CommentLikeId implements Serializable {

    private Long comment;
    private Long user;

    protected CommentLikeId() {
    }

    public CommentLikeId(Long comment, Long user) {
        this.comment = comment;
        this.user = user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CommentLikeId that)) {
            return false;
        }
        return Objects.equals(comment, that.comment) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comment, user);
    }
}
