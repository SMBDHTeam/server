package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "커서 기반 피드 목록. 다음 페이지는 nextCursor를 cursor 파라미터로 다시 보낸다.")
public record PostSummaryListResponse(
        @Schema(description = "최신순 게시물") List<PostSummaryResponse> items,
        @Schema(description = "다음 요청에 사용할 커서. 더 가져올 게시물이 없으면 null이다.",
                example = "81")
        Long nextCursor
) {
}
