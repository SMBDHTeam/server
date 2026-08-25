package com.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로필 사진 변경은 저장소에 올린 파일을 다루는 작업이라 별도 API 로 분리한다.
 */
@Schema(description = "닉네임 변경 요청")
public record NicknameUpdateRequest(
        @Schema(description = "바꿀 닉네임. 최대 10자이며 이미 쓰는 사람이 있으면 409 를 반환한다.",
                example = "감자")
        @NotBlank @Size(max = 10) String nickname
) {
}
