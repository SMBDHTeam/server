package com.server.bookmark.dto;

import com.server.post.dto.PostSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 북마크 목록. 최근 저장한 순으로 반환한다.")
public record BookmarkListResponse(
        @Schema(description = "저장한 게시물. 피드와 같은 축약 형태다.")
        List<PostSummaryResponse> items
) {
}
