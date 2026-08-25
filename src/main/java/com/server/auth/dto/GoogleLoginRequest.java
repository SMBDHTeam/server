package com.server.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "구글 로그인 요청. 프론트가 구글에서 받은 ID 토큰을 그대로 전달한다.")
public record GoogleLoginRequest(
        @Schema(description = "구글 ID 토큰(JWT)", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...")
        @NotBlank String idToken
) {
}
