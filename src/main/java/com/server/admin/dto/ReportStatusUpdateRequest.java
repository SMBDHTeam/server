package com.server.admin.dto;

import com.server.post.domain.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "신고 처리 상태 변경")
public record ReportStatusUpdateRequest(
        @Schema(description = "PENDING, REVIEWING, RESOLVED, REJECTED", example = "RESOLVED")
        @NotNull ReportStatus status
) {
}
