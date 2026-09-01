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

@DisplayName("알림 저장")
class NotificationWriterTest {

    private static final long RECIPIENT_ID = 1L;
    private static final long ACTOR_ID = 2L;
    private static final long POST_ID = 7L;

    private final NotificationRepository notificationRepository =
            Mockito.mock(NotificationRepository.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);

    private final NotificationWriter notificationWriter =
            new NotificationWriter(notificationRepository, userRepository);

    @Test
    @DisplayName("내가 한 행동은 나에게 알리지 않는다")
    void doesNotNotifySelf() {
        notificationWriter.write(
                RECIPIENT_ID,
                RECIPIENT_ID,
                NotificationType.POST_LIKE,
                NotificationTargetType.POST,
                POST_ID);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("받는 사람이 탈퇴했으면 남기지 않는다")
    void doesNotNotifyWithdrawnRecipient() {
        when(userRepository.findByIdAndDeletedAtIsNull(RECIPIENT_ID)).thenReturn(Optional.empty());

        // 알림은 부가 기능이라 받는 사람을 못 찾아도 원래 하던 작업을 실패시키지 않는다.
        notificationWriter.write(
                RECIPIENT_ID,
                ACTOR_ID,
                NotificationType.POST_LIKE,
                NotificationTargetType.POST,
                POST_ID);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("같은 대상에 같은 알림이 이미 있으면 다시 남기지 않는다")
    void doesNotNotifyTwiceForSameTarget() {
        when(userRepository.findByIdAndDeletedAtIsNull(RECIPIENT_ID))
                .thenReturn(Optional.of(user(RECIPIENT_ID)));
        // 좋아요를 취소했다 다시 눌러 관계가 새로 생긴 상황이다.
        when(notificationRepository
                .existsByRecipientIdAndActorIdAndTypeAndTargetTypeAndTargetId(
                        RECIPIENT_ID, ACTOR_ID, NotificationType.POST_LIKE,
                        NotificationTargetType.POST, POST_ID))
                .thenReturn(true);

        notificationWriter.write(
                RECIPIENT_ID,
                ACTOR_ID,
                NotificationType.POST_LIKE,
                NotificationTargetType.POST,
                POST_ID);

        verify(notificationRepository, never()).save(any(Notification.class));
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
