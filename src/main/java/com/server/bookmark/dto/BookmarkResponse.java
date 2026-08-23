package com.server.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크 처리 결과. 몇 명이 저장했는지는 공개하지 않는다.")
public record BookmarkResponse(
        @Schema(description = "요청한 사용자가 이 게시물을 저장한 상태인지", example = "true")
        boolean bookmarked
) {
}
