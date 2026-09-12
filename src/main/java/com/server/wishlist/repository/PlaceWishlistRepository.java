package com.server.wishlist.repository;

import com.server.wishlist.domain.PlaceWishlist;
import com.server.wishlist.domain.PlaceWishlistId;
import com.server.wishlist.dto.PlaceWishlistView;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceWishlistRepository extends JpaRepository<PlaceWishlist, PlaceWishlistId> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    long deleteByUserIdAndPlaceId(Long userId, Long placeId);

    long countByUserId(Long userId);

    /** 목록에 담긴 상태를 표시할 때, 장소 수와 무관하게 한 번만 조회한다. */
    @Query("""
            select wish.place.id from PlaceWishlist wish
            where wish.user.id = :userId and wish.place.id in :placeIds
            """)
    List<Long> findWishlistedPlaceIds(
            @Param("userId") Long userId, @Param("placeIds") Collection<Long> placeIds);

    /**
     * 최근에 담은 순으로 읽는다. 담은 시각 기준 정렬이라 커서로 삼을 단조 증가 값이 없어
     * 오프셋 페이징을 쓴다. 이 목록은 본인만 바꾸므로 조회 중 목록이 밀릴 위험이 낮다.
     *
     * <p>가려 둔 장소는 뺀다. 관리자가 숨긴 장소를 위시리스트에서는 계속 보여주면, 눌러서
     * 상세로 들어갔을 때 없는 장소가 된다.
     */
    @Query("""
            select new com.server.wishlist.dto.PlaceWishlistView(
                place.id, place.name, place.category, place.address,
                place.latitude, place.longitude, place.primaryImageUrl)
            from PlaceWishlist wish
            join wish.place place
            where wish.user.id = :userId
              and place.hiddenAt is null
            order by wish.createdAt desc
            """)
    List<PlaceWishlistView> findViewsByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 있으면 그대로 두고 없을 때만 넣는다. 확인 후 저장하면 같은 요청이 동시에 들어올 때
     * 양쪽 다 없다고 읽어 기본키 충돌로 한쪽이 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into place_wishlists (user_id, place_id, created_at)
            values (:userId, :placeId, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("placeId") Long placeId);
}
