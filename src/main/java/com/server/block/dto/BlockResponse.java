package com.server.block.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "차단 처리 결과")
public record BlockResponse(
        @Schema(description = "요청한 사용자가 대상을 차단한 상태인지", example = "true")
        boolean blocked
) {
}
