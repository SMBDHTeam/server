package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사용자 목록")
public record AdminUserListResponse(
        List<AdminUserResponse> items,
        @Schema(description = "조건에 맞는 전체 건수", example = "128") long totalCount
) {
}
