package com.server.notification.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.notification.domain.Notification;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.notification.dto.NotificationListResponse;
import com.server.notification.dto.NotificationResponse;
import com.server.notification.repository.NotificationRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림을 쌓고 읽는다. 알림은 커뮤니티 전용이 아니므로 이 서비스는 어떤 도메인에서
 * 부르는지 모른다. 알림을 만드는 쪽이 받는 사람과 대상만 넘기면 된다.
 *
 * <p>실시간 푸시는 없다. 저장해 두고 클라이언트가 조회한다. 나중에 푸시를 붙이더라도
 * {@link #notify} 뒤에 전송 한 줄을 더하는 형태라 이 구조는 그대로 쓸 수 있다.
 */
@Service
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    /** 첫 페이지는 커서가 없으므로 어떤 알림 ID보다 큰 값에서 시작한다. */
    private static final long FIRST_PAGE_CURSOR = Long.MAX_VALUE;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 알림을 남긴다. 다른 도메인에서 부르는 진입점이다.
     *
     * <p>내가 한 행동은 나에게 알리지 않는다. 받는 사람이 탈퇴했으면 남기지 않는다.
     * 알림은 부가 기능이므로, 받는 사람을 찾지 못해도 원래 하던 작업을 실패시키지 않는다.
     *
     * <p>같은 사람이 같은 대상에 같은 종류의 알림을 이미 남겼으면 다시 남기지 않는다.
     * 좋아요를 취소했다 다시 누르면 관계가 새로 생기는데, 그때마다 알리면 도배가 된다.
     * 댓글은 대상이 매번 새 댓글이라 이 규칙에 걸리지 않는다.
     */
    @Transactional
    public void notify(
            Long recipientId,
            Long actorId,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId
    ) {
        // 받는 사람을 찾는 조회가 비어 돌아올 수 있다. 알림 때문에 원래 작업이 깨지면 안 된다.
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

    /** 내 알림 목록. 목록을 열 때 벨 아이콘도 같이 갱신할 수 있게 안 읽은 수를 함께 준다. */
    @Transactional(readOnly = true)
    public NotificationListResponse getMyNotifications(Long userId, Long cursor, Integer size) {
        requireActiveUser(userId);
        int limit = resolvePageSize(size);
        List<Notification> notifications = notificationRepository.findMine(
                userId,
                cursor == null ? FIRST_PAGE_CURSOR : cursor,
                PageRequest.of(0, limit));

        // 요청한 개수를 못 채웠으면 더 가져올 알림이 없다.
        Long nextCursor = notifications.size() < limit
                ? null
                : notifications.get(notifications.size() - 1).getId();

        return new NotificationListResponse(
                notifications.stream().map(NotificationResponse::from).toList(),
                nextCursor,
                notificationRepository.countUnread(userId));
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        requireActiveUser(userId);
        return notificationRepository.countUnread(userId);
    }

    /** 이미 읽은 알림을 다시 읽어도 처음 읽은 시각을 유지한다. */
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isRecipient(userId)) {
            // 남의 알림은 있는지조차 알려주지 않는다.
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        notification.markAsRead();
        return NotificationResponse.from(notification);
    }

    /** 모두 읽음. 이미 다 읽은 상태에서 다시 불러도 아무 일도 일어나지 않는다. */
    @Transactional
    public long markAllAsRead(Long userId) {
        requireActiveUser(userId);
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        return notificationRepository.countUnread(userId);
    }

    private void requireActiveUser(Long userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private int resolvePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
