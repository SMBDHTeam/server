package com.server.admin.dto;

import com.server.admin.domain.AdminAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminActionResponse(
        @Schema(example = "12") Long id,

        @Schema(description = "조치한 관리자") Actor admin,

        @Schema(description = "USER_SUSPENDED, POST_DELETED, PLACE_HIDDEN 등", example = "USER_SUSPENDED")
        String action,

        @Schema(description = "USER, POST, COMMENT, REPORT, PLACE, SYSTEM", example = "USER")
        String targetType,

        @Schema(description = "대상 ID. 대상이 없는 조치면 null", example = "5") Long targetId,

        @Schema(description = "관리자가 입력한 사유", example = "광고성 게시물 반복") String reason,

        @Schema(description = "상태 전이나 실행 결과 같은 부가 정보", example = "만료 2026-09-15T00:00")
        String detail,

        @Schema(example = "2026-09-08T14:02:00") LocalDateTime createdAt
) {

    public record Actor(
            @Schema(example = "3") Long id,
            @Schema(example = "서동준") String nickname
    ) {
    }

    public static AdminActionResponse of(AdminAction action, Actor admin) {
        return new AdminActionResponse(
                action.getId(),
                admin,
                action.getAction().name(),
                action.getTargetType().name(),
                action.getTargetId(),
                action.getReason(),
                action.getDetail(),
                action.getCreatedAt());
    }
}
