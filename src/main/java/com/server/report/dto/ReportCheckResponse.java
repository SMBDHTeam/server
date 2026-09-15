package com.server.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내가 이 대상을 신고했는지.
 *
 * @param reported 이미 신고했으면 true. 화면이 사유를 다시 고르게 하지 않고 바로 안내한다
 */
@Schema(description = "신고 여부")
public record ReportCheckResponse(
        @Schema(example = "true") boolean reported
) {
}
