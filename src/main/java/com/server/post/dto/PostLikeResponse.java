package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좋아요 처리 결과. 누른 사용자 목록은 노출하지 않는다.")
public record PostLikeResponse(
        @Schema(description = "처리 후 좋아요 수", example = "13") int likeCount,
        @Schema(description = "요청한 사용자가 이 게시물에 좋아요를 누른 상태인지", example = "true")
        boolean liked
) {
}
