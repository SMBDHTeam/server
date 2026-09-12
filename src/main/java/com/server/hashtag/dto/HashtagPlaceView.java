package com.server.hashtag.dto;

import java.math.BigDecimal;

/**
 * 카테고리가 가리키는 장소 한 곳의 조회 결과.
 *
 * <p>{@code Object[]} 로 받아 순서대로 캐스팅하던 것을 대신한다. 그 방식은 질의의
 * {@code select} 순서를 바꿔도 컴파일러가 아무 말을 하지 않고, 실행할 때
 * {@code ClassCastException} 으로 터진다.
 */
public record HashtagPlaceView(
        Long placeId,
        String name,
        String category,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        long postCount,
        long authorCount
) {
}
