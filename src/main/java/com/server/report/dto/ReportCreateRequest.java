package com.server.report.dto;

import com.server.report.domain.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "신고 요청. 같은 대상은 한 번만 신고할 수 있다.")
public record ReportCreateRequest(
        @Schema(description = "신고 대상 종류", example = "POST")
        @NotNull ReportTargetType targetType,

        @Schema(description = "신고 대상 ID", example = "7")
        @NotNull Long targetId,

        @Schema(description = "신고 사유", example = "광고성 게시물입니다")
        @NotBlank @Size(max = 500) String reason
) {
}
