package com.server.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "액세스 토큰 갱신 요청")
public record RefreshRequest(
        @Schema(description = "로그인 또는 직전 갱신에서 받은 refreshToken",
                example = "42.Yk9sZ1p6d0hFV3ROanBRTXhLdG5jUQ")
        @NotBlank String refreshToken
) {
}
