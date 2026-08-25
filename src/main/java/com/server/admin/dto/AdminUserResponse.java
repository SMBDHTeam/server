package com.server.admin.dto;

import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 화면의 사용자 한 건")
public record AdminUserResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "여행자") String nickname,
        @Schema(example = "traveler@example.com") String email,
        @Schema(description = "USER 또는 ADMIN", example = "USER") UserRole role,
        @Schema(description = "ACTIVE, SUSPENDED, WITHDRAWN", example = "ACTIVE") UserStatus status,
        @Schema(description = "정지 만료 시각. 지나면 쓰기가 다시 열린다") LocalDateTime suspendedUntil,
        @Schema(example = "광고성 게시물 반복 등록") String suspendedReason,
        @Schema(description = "지금 쓰기가 막혀 있는지. 기간이 지난 정지는 false 다", example = "false")
        boolean writeBlocked,
        @Schema(example = "2026-08-25T14:38:55") LocalDateTime createdAt,
        @Schema(description = "탈퇴 시각. 탈퇴하지 않았으면 null") LocalDateTime deletedAt
) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getSuspendedUntil(),
                user.getSuspendedReason(),
                user.isWriteBlockedAt(LocalDateTime.now()),
                user.getCreatedAt(),
                user.getDeletedAt());
    }
}
