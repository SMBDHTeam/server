package com.server.report.dto;

import com.server.report.domain.Report;
import com.server.report.domain.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "접수된 신고")
public record ReportResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "POST") ReportTargetType targetType,
        @Schema(example = "7") Long targetId,
        @Schema(description = "처리 상태. 접수 시점에는 PENDING 이다.", example = "PENDING")
        String status,
        @Schema(example = "2026-08-24T03:40:00") LocalDateTime createdAt
) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getStatus().name(),
                report.getCreatedAt());
    }
}
