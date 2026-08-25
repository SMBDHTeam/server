package com.server.notification.dto;

import com.server.notification.domain.Notification;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "알림 한 건. 화면 문구는 type 과 actor 를 보고 클라이언트가 만든다.")
public record NotificationResponse(
        @Schema(example = "12") Long id,
        @Schema(description = "알림 종류", example = "POST_LIKE") NotificationType type,
        @Schema(description = "행동한 사람. 시스템 알림이면 null 이다.")
        Actor actor,
        @Schema(description = "눌렀을 때 이동할 대상의 종류", example = "POST")
        NotificationTargetType targetType,
        @Schema(description = "눌렀을 때 이동할 대상 ID. 대상이 지워졌을 수 있다.", example = "7")
        Long targetId,
        @Schema(description = "읽었는지", example = "false") boolean read,
        @Schema(example = "2026-08-25T11:20:00") LocalDateTime createdAt
) {

    @Schema(description = "행동한 사람")
    public record Actor(
            @Schema(example = "2") Long id,
            @Schema(example = "고구마") String nickname,
            @Schema(example = "https://example.com/profile/2.jpg") String profileImageUrl
    ) {

        private static Actor from(User user) {
            return new Actor(user.getId(), user.getNickname(), user.getProfileImageUrl());
        }
    }

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getActor() == null ? null : Actor.from(notification.getActor()),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
