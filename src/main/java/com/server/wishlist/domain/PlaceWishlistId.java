package com.server.wishlist.domain;

import java.io.Serializable;
import java.util.Objects;

/** {@link PlaceWishlist}의 복합 기본키. 같은 장소를 두 번 담을 수 없다는 제약을 DB 가 보장한다. */
public class PlaceWishlistId implements Serializable {

    private Long user;
    private Long place;

    protected PlaceWishlistId() {
    }

    public PlaceWishlistId(Long user, Long place) {
        this.user = user;
        this.place = place;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlaceWishlistId that)) {
            return false;
        }
        return Objects.equals(user, that.user) && Objects.equals(place, that.place);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, place);
    }
}
