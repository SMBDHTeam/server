package com.server.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.notification.domain.Notification;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.notification.repository.NotificationRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("알림 서비스")
class NotificationServiceTest {

    private static final long RECIPIENT_ID = 1L;
    private static final long ACTOR_ID = 2L;
    private static final long POST_ID = 7L;

    private final NotificationRepository notificationRepository =
            Mockito.mock(NotificationRepository.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);

    private final NotificationWriter notificationWriter = Mockito.mock(NotificationWriter.class);

    private final NotificationService notificationService =
            new NotificationService(notificationRepository, notificationWriter, userRepository);

    @Test
    @DisplayName("저장이 실패해도 부르는 쪽 작업을 막지 않는다")
    void swallowsWriteFailure() {
        Mockito.doThrow(new IllegalStateException("DB 연결 실패"))
                .when(notificationWriter).write(any(), any(), any(), any(), any());

        // 예외가 밖으로 나가면 팔로우나 댓글 작성이 알림 때문에 함께 롤백된다.
        notificationService.notify(
                RECIPIENT_ID, ACTOR_ID, NotificationType.POST_LIKE,
                NotificationTargetType.POST, POST_ID);
    }

    @Test
    @DisplayName("남의 알림은 있는지조차 알려주지 않는다")
    void hidesOtherUsersNotification() {
        Notification notification = notification(RECIPIENT_ID);
        when(notificationRepository.findById(12L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(12L, ACTOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다")
    void keepsFirstReadAt() {
        Notification notification = notification(RECIPIENT_ID);
        LocalDateTime firstReadAt = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(notification, "readAt", firstReadAt);
        when(notificationRepository.findById(12L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(12L, RECIPIENT_ID);

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    private Notification notification(long recipientId) {
        Notification notification = new Notification(
                user(recipientId),
                user(ACTOR_ID),
                NotificationType.POST_LIKE,
                NotificationTargetType.POST,
                POST_ID);
        ReflectionTestUtils.setField(notification, "id", 12L);
        return notification;
    }

    private User user(long userId) {
        User user = new User("사용자" + userId, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
