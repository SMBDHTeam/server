package com.server.wishlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "위시리스트에 담긴 장소 한 곳")
public record PlaceWishlistResponse(
        @Schema(example = "42") Long placeId,
        @Schema(example = "광안리해수욕장") String name,
        @Schema(description = "장소 분류. 없으면 null 이다.", example = "관광지") String category,
        @Schema(example = "부산 수영구 광안해변로 219") String address,
        @Schema(example = "35.15320000") BigDecimal latitude,
        @Schema(example = "129.11860000") BigDecimal longitude,
        @Schema(description = "대표 이미지. 없는 장소가 많다.",
                example = "https://example.com/place/42.jpg")
        String imageUrl
) {

    public static PlaceWishlistResponse from(PlaceWishlistView view) {
        return new PlaceWishlistResponse(
                view.placeId(), view.name(), view.category(), view.address(),
                view.latitude(), view.longitude(), view.imageUrl());
    }
}
