package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 댓글 삭제 응답. 갱신된 게시물 댓글 수만 담는다.
 *
 * <p>본문 없이 {@code 204} 로 돌려주면 화면이 새 숫자를 알 방법이 없어 게시물을 다시
 * 조회해야 한다. 작성 응답이 {@code postCommentCount} 를 담고 있으므로 삭제도 맞춘다.
 */
@Schema(description = "댓글 삭제 결과")
public record CommentDeleteResponse(
        @Schema(description = "이 댓글이 지워진 뒤 게시물의 전체 댓글 수", example = "3")
        int postCommentCount
) {
}
