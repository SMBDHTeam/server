package com.server.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "발급된 토큰과 로그인한 사용자")
public record AuthTokenResponse(
        @Schema(description = "Authorization: Bearer 에 실어 보낸다")
        String accessToken,

        @Schema(description = "액세스 토큰 만료까지 남은 초", example = "1800")
        long expiresIn,

        @Schema(description = "갱신용 토큰. 갱신할 때마다 새 값으로 바뀌므로 항상 최신 값을 보관한다",
                example = "42.Yk9sZ1p6d0hFV3ROanBRTXhLdG5jUQ")
        String refreshToken,

        @Schema(description = "로그인한 사용자")
        AuthUser user
) {

    @Schema(description = "로그인한 사용자 요약")
    public record AuthUser(
            @Schema(example = "42") Long id,
            @Schema(example = "동준") String nickname,
            @Schema(example = "https://example.com/p.png") String profileImageUrl,
            @Schema(description = "USER 또는 ADMIN", example = "USER") String role
    ) {
    }
}
