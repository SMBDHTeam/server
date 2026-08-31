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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationWriter notificationWriter;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationWriter notificationWriter,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationWriter = notificationWriter;
        this.userRepository = userRepository;
    }

    /**
     * 알림을 남긴다. 다른 도메인에서 부르는 진입점이다.
     *
     * <p>알림은 부가 기능이라 실패해도 부르는 쪽 작업을 막지 않는다. 저장은 별도
     * 트랜잭션({@link NotificationWriter})에서 하고, 여기서는 예외를 삼키고 로그만 남긴다.
     * 팔로우나 댓글 작성이 알림 때문에 함께 롤백되면 사용자는 원인을 알 수 없다.
     */
    public void notify(
            Long recipientId,
            Long actorId,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId
    ) {
        try {
            notificationWriter.write(recipientId, actorId, type, targetType, targetId);
        } catch (RuntimeException exception) {
            log.warn("알림을 남기지 못했다. recipientId={}, type={}, targetId={}",
                    recipientId, type, targetId, exception);
        }
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
