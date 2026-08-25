package com.server.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 본문만 수정한다. 첨부 미디어와 장소 태그 교체는 저장소에 올라간 파일 정리까지 함께
 * 정해야 하므로 후속 작업으로 미룬다.
 */
@Schema(description = "게시물 수정 요청. 작성자 본인만 수정할 수 있다.")
public record PostUpdateRequest(
        @Schema(description = "수정할 본문. 최대 2000자다.", example = "광안리 야경 진짜 좋았어요")
        @NotBlank @Size(max = PostCreateRequest.MAX_CONTENT_LENGTH) String content
) {
}
