package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "댓글 좋아요 처리 결과")
public record CommentLikeResponse(
        @Schema(description = "처리 후 좋아요 수", example = "3") int likeCount,
        @Schema(description = "요청한 사용자가 이 댓글에 좋아요를 누른 상태인지", example = "true")
        boolean liked
) {
}
