package com.server.wishlist.domain;

import com.server.place.domain.Place;
import com.server.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 나중에 가 보려고 담아 둔 장소.
 *
 * <p>일정을 만들 때 필수 방문지로 넘기는 것이 주 용도다. 게시물 저장({@code bookmarks})과
 * 구조는 같지만 대상이 장소라 테이블을 나눈다. 한 테이블에 합치면 대상 종류 컬럼이 생기고
 * 외래키를 걸 수 없어, 지워진 대상을 가리키는 행이 남는다.
 */
@Entity
@Table(name = "place_wishlists")
@IdClass(PlaceWishlistId.class)
public class PlaceWishlist {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PlaceWishlist() {
    }

    public PlaceWishlist(User user, Place place) {
        this.user = user;
        this.place = place;
        this.createdAt = LocalDateTime.now();
    }

    public User getUser() {
        return user;
    }

    public Place getPlace() {
        return place;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
