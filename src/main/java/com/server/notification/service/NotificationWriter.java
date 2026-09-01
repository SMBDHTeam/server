package com.server.notification.service;

import com.server.notification.domain.Notification;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.notification.repository.NotificationRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 한 건을 독립된 트랜잭션에서 저장한다.
 *
 * <p>{@link NotificationService} 와 나눠 둔 이유가 있다. 알림 저장이 실패하면 그 예외를
 * 삼켜야 하는데, 예외를 잡는 자리가 트랜잭션 안이면 이미 롤백 표시가 붙어 커밋 시점에
 * 다시 터진다. 잡는 쪽과 트랜잭션을 여는 쪽이 다른 빈이어야 한다.
 *
 * <p>{@code REQUIRES_NEW} 라 부르는 쪽 트랜잭션과 분리된다. 팔로우나 댓글 작성이 알림
 * 때문에 함께 롤백되지 않는다.
 */
@Component
public class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationWriter(
            NotificationRepository notificationRepository,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 내가 한 행동은 나에게 알리지 않는다. 받는 사람이 탈퇴했으면 남기지 않는다.
     *
     * <p>같은 사람이 같은 대상에 같은 종류의 알림을 이미 남겼으면 다시 남기지 않는다.
     * 좋아요를 취소했다 다시 누르면 관계가 새로 생기는데, 그때마다 알리면 도배가 된다.
     * 댓글은 대상이 매번 새 댓글이라 이 규칙에 걸리지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(
            Long recipientId,
            Long actorId,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId
    ) {
        // 받는 사람을 찾는 조회가 비어 돌아올 수 있다.
        if (recipientId == null || recipientId.equals(actorId)) {
            return;
        }
        User recipient = userRepository.findByIdAndDeletedAtIsNull(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        if (notificationRepository.existsByRecipientIdAndActorIdAndTypeAndTargetTypeAndTargetId(
                recipientId, actorId, type, targetType, targetId)) {
            return;
        }
        User actor = actorId == null
                ? null
                : userRepository.findByIdAndDeletedAtIsNull(actorId).orElse(null);

        notificationRepository.save(
                new Notification(recipient, actor, type, targetType, targetId));
    }
}
