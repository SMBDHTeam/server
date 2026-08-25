package com.server.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "알림 목록. 최신순이며 커서 페이징이다.")
public record NotificationListResponse(
        List<NotificationResponse> items,
        @Schema(description = "다음 요청의 cursor 로 넘긴다. null 이면 더 없다.", example = "9")
        Long nextCursor,
        @Schema(description = "읽지 않은 알림 수. 벨 아이콘 표시에 쓴다.", example = "3")
        long unreadCount
) {
}
