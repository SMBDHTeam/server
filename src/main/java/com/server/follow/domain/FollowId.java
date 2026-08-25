package com.server.follow.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link Follow}의 복합 기본키. 같은 사람을 두 번 팔로우할 수 없다는 제약을 DB가 보장한다.
 */
public class FollowId implements Serializable {

    private Long follower;
    private Long following;

    protected FollowId() {
    }

    public FollowId(Long follower, Long following) {
        this.follower = follower;
        this.following = following;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FollowId that)) {
            return false;
        }
        return Objects.equals(follower, that.follower) && Objects.equals(following, that.following);
    }

    @Override
    public int hashCode() {
        return Objects.hash(follower, following);
    }
}
