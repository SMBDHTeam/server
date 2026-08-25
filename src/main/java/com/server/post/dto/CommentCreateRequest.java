package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "댓글 작성 요청. 작성자는 X-User-Id 헤더로 전달한다.")
public record CommentCreateRequest(
        @Schema(description = "댓글 내용. 최대 1000자다.", example = "저도 여기 가봤는데 좋았어요")
        @NotBlank @Size(max = MAX_CONTENT_LENGTH) String content,

        @Schema(description = "답글을 달 부모 댓글 ID. 일반 댓글이면 생략한다. "
                + "대댓글에는 다시 답글을 달 수 없다.", example = "3")
        Long parentId
) {

    public static final int MAX_CONTENT_LENGTH = 1000;
}
