package com.server.hashtag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "해시태그가 가리키는 장소 한 곳")
public record HashtagPlaceResponse(
        @Schema(example = "42") Long placeId,
        @Schema(example = "광안리해수욕장") String name,
        @Schema(description = "장소 분류 원본. TourAPI 는 분류코드(A02030400)가, 지도 검색으로 "
                + "등록한 곳은 제공자가 준 문자열이 들어간다. 화면에는 categoryLabel 을 쓴다.",
                example = "A02030400")
        String category,
        @Schema(description = "화면 표시용 카테고리 라벨. 분류코드를 사람이 읽는 이름으로 바꾼 값이다.",
                example = "음식점")
        String categoryLabel,
        @Schema(example = "부산 수영구 광안해변로 219") String address,
        @Schema(example = "35.15320000") BigDecimal latitude,
        @Schema(example = "129.11860000") BigDecimal longitude,
        @Schema(description = "이 태그로 이 장소를 언급한 게시물 수", example = "27") long postCount,
        @Schema(description = "이 태그로 이 장소를 언급한 사람 수", example = "19") long authorCount
) {
}
