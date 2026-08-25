package com.server.admin.dto;

import com.server.post.domain.ReportStatus;
import com.server.report.domain.Report;
import com.server.report.domain.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "신고 한 건")
public record AdminReportResponse(
        @Schema(example = "12") Long id,
        @Schema(description = "신고한 사용자") Reporter reporter,
        @Schema(description = "POST, COMMENT, USER", example = "POST") ReportTargetType targetType,
        @Schema(example = "7") Long targetId,
        @Schema(example = "광고성 게시물입니다") String reason,
        @Schema(description = "PENDING, REVIEWING, RESOLVED, REJECTED", example = "PENDING")
        ReportStatus status,
        @Schema(example = "2026-08-25T14:02:00") LocalDateTime createdAt,
        @Schema(description = "처리한 관리자. 아직 처리 전이면 null") Reporter handledBy,
        @Schema(description = "처리 시각. 아직 처리 전이면 null") LocalDateTime handledAt
) {

    @Schema(description = "사용자 요약")
    public record Reporter(
            @Schema(example = "3") Long id,
            @Schema(example = "여행자") String nickname
    ) {
    }

    public static AdminReportResponse from(Report report) {
        return new AdminReportResponse(
                report.getId(),
                new Reporter(report.getReporter().getId(), report.getReporter().getNickname()),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getHandledBy() == null ? null
                        : new Reporter(report.getHandledBy().getId(), report.getHandledBy().getNickname()),
                report.getHandledAt());
    }
}
