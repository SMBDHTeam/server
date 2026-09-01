package com.server.media.dto;

import com.server.post.domain.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param url       게시물 작성 요청의 {@code mediaList[].url} 에 그대로 넣는다
 * @param mediaType 같은 요청의 {@code mediaList[].mediaType} 에 그대로 넣는다
 */
@Schema(name = "MediaUpload", description = "업로드된 파일 한 건")
public record MediaUploadResponse(
        @Schema(description = "공개 URL", example =
                "https://smbdh-community-media.s3.ap-northeast-2.amazonaws.com/posts/2026/08/a1b2.jpg")
        String url,

        @Schema(description = "파일 내용으로 판별한 종류", example = "IMAGE")
        MediaType mediaType
) {
}
