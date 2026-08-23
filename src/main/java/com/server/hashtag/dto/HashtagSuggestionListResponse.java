package com.server.hashtag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "해시태그 자동완성 결과. 많이 쓰인 태그를 먼저 반환한다.")
public record HashtagSuggestionListResponse(
        List<HashtagSuggestionResponse> items
) {
}
