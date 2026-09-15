package com.server.report.dto;

import com.server.report.domain.ReportReasonType;
import com.server.report.domain.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "신고 요청. 같은 대상은 한 번만 신고할 수 있다.")
public record ReportCreateRequest(
        @Schema(description = "신고 대상 종류", example = "POST")
        @NotNull ReportTargetType targetType,

        @Schema(description = "신고 대상 ID", example = "7")
        @NotNull Long targetId,

        @Schema(description = "사유 유형. SPAM, ABUSE, SEXUAL, ILLEGAL, PRIVACY, FALSE_INFO, OTHER",
                example = "SPAM")
        @NotNull ReportReasonType reasonType,

        @Schema(description = "덧붙이는 설명. OTHER 면 필수, 나머지는 생략할 수 있다",
                example = "같은 링크를 반복해서 올립니다")
        @Size(max = 500) String reason
) {

    @Schema(hidden = true)
    @AssertTrue(message = "기타 사유는 설명이 필요합니다.")
    public boolean isReasonPresentWhenOther() {
        return reasonType == null || !reasonType.requiresDetail()
                || (reason != null && !reason.isBlank());
    }
}
