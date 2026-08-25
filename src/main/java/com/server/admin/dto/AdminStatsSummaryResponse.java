package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "총계와 기간 증감. 기간은 days 로 지정하며 기본 7일이다.")
public record AdminStatsSummaryResponse(
        @Schema(description = "집계 기간(일)", example = "7") int days,
        Metric users,
        Metric posts,
        Metric schedules,
        @Schema(description = "처리 대기 중인 신고 수", example = "4") long pendingReports,
        @Schema(description = "정지된 사용자 수", example = "2") long suspendedUsers,
        @Schema(description = "적재된 장소 수. 가린 것은 뺀다", example = "475") long visiblePlaces
) {

    @Schema(description = "총계와 최근 기간 증가분")
    public record Metric(
            @Schema(description = "전체 누적", example = "128") long total,
            @Schema(description = "최근 기간 안에 늘어난 수", example = "12") long recent
    ) {
    }
}
