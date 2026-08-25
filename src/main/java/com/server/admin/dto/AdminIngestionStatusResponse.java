package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;

@Schema(description = "TourAPI 적재 상태와 오늘 남은 호출 예산")
public record AdminIngestionStatusResponse(
        @Schema(description = "ingestion_status 별 장소 수", example = "{\"SYNCED\":475,\"PENDING\":12}")
        Map<String, Long> statusCounts,

        @Schema(description = "가려 둔 장소 수", example = "3") long hiddenCount,

        @Schema(description = "적재 스케줄러가 켜져 있는지", example = "true") boolean ingestionEnabled,

        @Schema(description = "상세 보강이 켜져 있는지. 끄면 같은 예산으로 약 3배 많은 장소를 발견한다",
                example = "true")
        boolean enrichmentEnabled,

        @Schema(description = "예산 기준일(KST)", example = "2026-08-25") LocalDate quotaDate,

        @Schema(description = "오늘 쓴 호출 수", example = "412") int requestsUsed,

        @Schema(description = "하루 한도", example = "900") int dailyLimit,

        @Schema(description = "오늘 남은 호출 수. 수동 적재 전에 확인한다", example = "488")
        int requestsRemaining
) {
}
