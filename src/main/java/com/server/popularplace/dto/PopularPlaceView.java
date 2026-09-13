package com.server.popularplace.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 커뮤니티에서 많이 언급된 장소 한 곳의 집계 결과.
 *
 * @param lastTaggedAt 마지막으로 태그된 시각. 언급한 사람 수가 같을 때 순서를 가른다
 */
public record PopularPlaceView(
        Long placeId,
        String name,
        String category,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        long postCount,
        long authorCount,
        LocalDateTime lastTaggedAt
) {
}
