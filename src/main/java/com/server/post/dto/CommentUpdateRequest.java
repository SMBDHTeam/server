package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "댓글 수정 요청. 작성자 본인만 수정할 수 있다.")
public record CommentUpdateRequest(
        @Schema(description = "수정할 내용. 최대 1000자다.", example = "저도 여기 가봤는데 좋았어요")
        @NotBlank @Size(max = CommentCreateRequest.MAX_CONTENT_LENGTH) String content
) {
}
