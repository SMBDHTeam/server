package com.server.bookmark.domain;

import java.io.Serializable;
import java.util.Objects;

/** {@link Bookmark}의 복합 기본키. 같은 게시물을 두 번 저장할 수 없다는 제약을 DB가 보장한다. */
public class BookmarkId implements Serializable {

    private Long user;
    private Long post;

    protected BookmarkId() {
    }

    public BookmarkId(Long user, Long post) {
        this.user = user;
        this.post = post;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BookmarkId that)) {
            return false;
        }
        return Objects.equals(user, that.user) && Objects.equals(post, that.post);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, post);
    }
}
