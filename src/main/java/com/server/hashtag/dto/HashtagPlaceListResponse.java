package com.server.hashtag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "해시태그로 모은 장소 목록. 언급한 사람이 많은 순이다.")
public record HashtagPlaceListResponse(
        List<HashtagPlaceResponse> items
) {
}
