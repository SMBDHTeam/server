package com.server.wishlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 담기·빼기 결과.
 *
 * @param wishlisted 처리 후 담겨 있는 상태인지. 화면이 하트를 바로 바꿀 수 있게 담는다
 */
@Schema(description = "위시리스트 담기·빼기 결과")
public record PlaceWishlistToggleResponse(
        @Schema(example = "true") boolean wishlisted
) {
}
