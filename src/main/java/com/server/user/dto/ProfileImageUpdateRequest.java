package com.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

/**
 * 사진을 지우는 것과 그대로 두는 것을 한 요청으로 구분할 수 없어, 제거는 별도 DELETE 로 나눈다.
 */
@Schema(description = "프로필 사진 변경 요청. 이미 업로드된 URL 을 전달한다.")
public record ProfileImageUpdateRequest(
        @Schema(description = "바꿀 프로필 사진 URL",
                example = "https://example.com/profile/1.jpg")
        @NotBlank String profileImageUrl
) {
}
