package com.server.notification.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.notification.dto.NotificationListResponse;
import com.server.notification.dto.NotificationResponse;
import com.server.notification.dto.UnreadCountResponse;
import com.server.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
@Tag(name = "알림", description = "내 알림 조회와 읽음 처리")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(
            summary = "내 알림 목록",
            description = "최신순 커서 페이징이다. 안 읽은 수를 함께 주므로 목록을 열 때 "
                    + "벨 아이콘 표시도 같이 갱신할 수 있다."
    )
    public NotificationListResponse getMyNotifications(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "9") @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 알림 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        Long userId = LoginUser.require(loginUser);
        return notificationService.getMyNotifications(userId, cursor, size);
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "읽지 않은 알림 수",
            description = "벨 아이콘 표시용이다. 목록 전체를 받지 않고 수만 확인할 때 쓴다."
    )
    public UnreadCountResponse getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUser loginUser
    ) {
        Long userId = LoginUser.require(loginUser);
        return new UnreadCountResponse(notificationService.countUnread(userId));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
            summary = "알림 읽음",
            description = "이미 읽은 알림을 다시 요청해도 처음 읽은 시각을 유지한다. "
                    + "남의 알림이면 404 를 반환한다."
    )
    public NotificationResponse markAsRead(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "12") @PathVariable Long notificationId
    ) {
        Long userId = LoginUser.require(loginUser);
        return notificationService.markAsRead(notificationId, userId);
    }

    @PatchMapping("/read-all")
    @Operation(
            summary = "알림 모두 읽음",
            description = "이미 다 읽은 상태에서 다시 요청해도 아무 일도 일어나지 않는다."
    )
    public UnreadCountResponse markAllAsRead(
            @AuthenticationPrincipal AuthenticatedUser loginUser
    ) {
        Long userId = LoginUser.require(loginUser);
        return new UnreadCountResponse(notificationService.markAllAsRead(userId));
    }
}
