package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "신고 목록. 상태·대상 유형으로 거를 수 있다.")
public record AdminReportListResponse(
        List<AdminReportResponse> items,
        @Schema(description = "조건에 맞는 전체 건수. 화면이 페이지 수를 계산한다", example = "37")
        long totalCount
) {
}
