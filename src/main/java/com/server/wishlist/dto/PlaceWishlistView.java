package com.server.wishlist.dto;

import java.math.BigDecimal;

/**
 * 위시리스트 한 줄의 조회 결과.
 *
 * <p>{@code Place} 엔티티를 그대로 읽으면 {@code mappedBy} 로 연결된 상세·운영정보가
 * 장소마다 추가 질의를 일으킨다. 목록은 이름과 좌표만 쓰므로 필요한 열만 가져온다.
 */
public record PlaceWishlistView(
        Long placeId,
        String name,
        String category,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl
) {
}
