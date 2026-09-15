package com.server.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 차단·해제 결과.
 *
 * @param following 처리 후 팔로우 상태. 차단하면 서로의 팔로우가 끊기므로 화면이 팔로우
 *                  버튼도 함께 바꿔야 한다. 그 값을 알려주지 않으면 프로필을 다시 불러야 한다
 */
@Schema(description = "차단 처리 결과")
public record BlockResponse(
        @Schema(description = "요청한 사용자가 대상을 차단한 상태인지", example = "true")
        boolean blocked,
        @Schema(description = "처리 후 요청자가 대상을 팔로우한 상태인지. 차단하면 끊기므로 "
                + "차단 직후에는 항상 false 다.", example = "false")
        boolean following
) {
}
