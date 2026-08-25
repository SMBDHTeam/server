package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

@Schema(description = "장소 숨김 또는 해제")
public record PlaceHiddenUpdateRequest(
        @Schema(description = "true 면 숨김, false 면 해제", example = "true") boolean hidden,

        @Schema(description = "숨기는 사유. 해제할 때는 생략할 수 있다", example = "좌표가 실제 위치와 다름")
        @Size(max = 500) String reason
) {

    @AssertTrue(message = "숨길 때는 사유가 필요합니다.")
    public boolean isReasonPresentWhenHiding() {
        return !hidden || (reason != null && !reason.isBlank());
    }
}
