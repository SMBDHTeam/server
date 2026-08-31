package com.server.hashtag.dto;

import com.server.hashtag.domain.Hashtag;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "해시태그 자동완성 후보")
public record HashtagSuggestionResponse(
        @Schema(description = "# 을 뺀 태그 이름", example = "맛집") String name,
        @Schema(description = "이 태그가 달린 게시물 수", example = "1203") int postCount
) {

    public static HashtagSuggestionResponse from(Hashtag hashtag) {
        return new HashtagSuggestionResponse(hashtag.getName(), hashtag.getPostCount());
    }
}
