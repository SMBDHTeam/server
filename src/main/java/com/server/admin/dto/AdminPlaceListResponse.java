package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminPlaceListResponse(
        List<AdminPlaceResponse> items,

        @Schema(description = "조건에 맞는 전체 건수. 화면이 페이지 수를 계산한다", example = "475")
        long totalCount
) {
}
