package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "사용자 정지 또는 해제")
public record UserStatusUpdateRequest(
        @Schema(description = "true 면 정지, false 면 해제", example = "true")
        boolean suspended,

        @Schema(description = "정지 기간(일). 생략하면 기한 없는 정지다. 해제할 때는 무시한다",
                example = "7")
        @Min(1) @Max(3650) Integer days,

        @Schema(description = "정지 사유. 해제할 때는 생략할 수 있다",
                example = "광고성 게시물 반복 등록")
        @Size(max = 500) String reason
) {

    @AssertTrue(message = "정지할 때는 사유가 필요합니다.")
    public boolean isReasonPresentWhenSuspending() {
        return !suspended || (reason != null && !reason.isBlank());
    }
}
