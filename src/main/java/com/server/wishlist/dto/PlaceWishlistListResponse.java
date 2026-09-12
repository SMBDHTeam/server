package com.server.wishlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 위시리스트. 최근에 담은 순이다.")
public record PlaceWishlistListResponse(List<PlaceWishlistResponse> items) {
}
