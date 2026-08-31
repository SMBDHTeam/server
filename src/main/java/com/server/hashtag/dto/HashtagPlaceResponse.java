package com.server.hashtag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "해시태그가 가리키는 장소 한 곳")
public record HashtagPlaceResponse(
        @Schema(example = "42") Long placeId,
        @Schema(example = "광안리해수욕장") String name,
        @Schema(description = "장소 분류. 없으면 null 이다.", example = "관광지") String category,
        @Schema(example = "부산 수영구 광안해변로 219") String address,
        @Schema(example = "35.15320000") BigDecimal latitude,
        @Schema(example = "129.11860000") BigDecimal longitude,
        @Schema(description = "이 태그로 이 장소를 언급한 게시물 수", example = "27") long postCount,
        @Schema(description = "이 태그로 이 장소를 언급한 사람 수", example = "19") long authorCount
) {
}
