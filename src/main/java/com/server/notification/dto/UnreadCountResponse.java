package com.server.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "읽지 않은 알림 수")
public record UnreadCountResponse(
        @Schema(example = "3") long unreadCount
) {
}
