package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "커서 기반 댓글 목록. 다음 페이지는 nextCursor 를 cursor 파라미터로 다시 보낸다.")
public record CommentListResponse(
        @Schema(description = "오래된 순 최상위 댓글. 각 항목의 replies 에 답글이 들어 있다.")
        List<CommentResponse> items,
        @Schema(description = "다음 요청에 사용할 커서. 더 가져올 댓글이 없으면 null 이다.", example = "12")
        Long nextCursor
) {
}
