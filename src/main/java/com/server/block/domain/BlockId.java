package com.server.block.domain;

import java.io.Serializable;
import java.util.Objects;

/** {@link Block}의 복합 기본키. 같은 사람을 두 번 차단할 수 없다. */
public class BlockId implements Serializable {

    private Long blocker;
    private Long blocked;

    protected BlockId() {
    }

    public BlockId(Long blocker, Long blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockId that)) {
            return false;
        }
        return Objects.equals(blocker, that.blocker) && Objects.equals(blocked, that.blocked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocker, blocked);
    }
}
