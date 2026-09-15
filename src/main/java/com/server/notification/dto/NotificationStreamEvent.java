package com.server.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실시간 알림 이벤트")
public record NotificationStreamEvent(
        NotificationResponse notification,
        @Schema(description = "이벤트 전송 시점의 읽지 않은 알림 수", example = "3")
        long unreadCount
) {
}
