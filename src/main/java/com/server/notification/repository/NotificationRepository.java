package com.server.notification.repository;

import com.server.notification.domain.Notification;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 내 알림 한 페이지. 최신순이라 피드와 같은 커서 방식을 쓴다.
     * 행동한 사람은 화면에 항상 필요하므로 함께 조회한다.
     *
     * <p>탈퇴한 사람이 남긴 알림은 보여줄 이름이 없어 제외한다. 시스템 알림처럼 행동한
     * 사람이 없는 알림은 그대로 둔다.
     */
    @EntityGraph(attributePaths = "actor")
    @Query("""
            select notification from Notification notification
            where notification.recipient.id = :recipientId
              and notification.id < :cursor
              and (notification.actor is null or notification.actor.deletedAt is null)
            order by notification.id desc
            """)
    List<Notification> findMine(
            @Param("recipientId") Long recipientId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    Optional<Notification> findById(Long id);

    /**
     * 같은 사람이 같은 대상에 같은 종류의 알림을 이미 남겼는지. 좋아요를 취소했다 다시
     * 누르면 관계가 새로 생겨 알림도 새로 만들어지는데, 그대로 두면 알림이 도배된다.
     */
    boolean existsByRecipientIdAndActorIdAndTypeAndTargetTypeAndTargetId(
            Long recipientId,
            Long actorId,
            NotificationType type,
            NotificationTargetType targetType,
            Long targetId);

    @Query("""
            select count(notification) from Notification notification
            where notification.recipient.id = :recipientId
              and notification.readAt is null
              and (notification.actor is null or notification.actor.deletedAt is null)
            """)
    long countUnread(@Param("recipientId") Long recipientId);

    /**
     * 안 읽은 알림을 한 번에 읽음 처리한다. 엔티티를 하나씩 읽어 바꾸면 알림이 많을 때
     * 그 수만큼 쿼리가 나간다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
            set notification.readAt = :readAt
            where notification.recipient.id = :recipientId
              and notification.readAt is null
            """)
    int markAllAsRead(
            @Param("recipientId") Long recipientId,
            @Param("readAt") LocalDateTime readAt);
}
