package com.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 상세. 조치를 판단하는 데 필요한 요약을 함께 준다.")
public record AdminUserDetailResponse(
        AdminUserResponse user,
        @Schema(description = "작성한 게시물 수. 삭제한 것은 빼고 센다", example = "12") long postCount,
        @Schema(description = "이 사용자가 접수한 신고 수", example = "3") long reportsFiled,
        @Schema(description = "이 사용자를 대상으로 접수된 신고 수. 조치 판단의 핵심이다", example = "5")
        long reportsReceived
) {
}
