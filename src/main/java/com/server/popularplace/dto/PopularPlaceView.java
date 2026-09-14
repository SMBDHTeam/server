package com.server.popularplace.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 커뮤니티에서 많이 언급된 장소 한 곳의 집계 결과.
 *
 * @param category      TourAPI 분류코드이거나 외부 제공자가 준 자유 형식 문자열이다
 * @param contentTypeId TourAPI 콘텐츠 유형. 분류코드가 없을 때 라벨을 정하는 데 쓴다
 * @param lastTaggedAt  마지막으로 태그된 시각. 언급한 사람 수가 같을 때 순서를 가른다
 */
public record PopularPlaceView(
        Long placeId,
        String name,
        String category,
        String contentTypeId,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String primaryImageUrl,
        long postCount,
        long authorCount,
        LocalDateTime lastTaggedAt
) {
}
