package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminActionListResponse(
        List<AdminActionResponse> items,

        @Schema(description = "조건에 맞는 전체 건수", example = "37") long totalCount
) {
}
