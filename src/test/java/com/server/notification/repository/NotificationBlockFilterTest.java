package com.server.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.block.repository.BlockRepository;
import com.server.notification.domain.Notification;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단 관계는 알림 조회 쿼리 안에서 걸러진다. 방향이 둘이고 시스템 알림 예외가 있어
 * 실제 쿼리로 확인한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("알림 차단 필터")
class NotificationBlockFilterTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private EntityManager entityManager;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = new User("나", null);
        other = new User("상대", null);
        entityManager.persist(me);
        entityManager.persist(other);
        entityManager.persist(new Notification(
                me, other, NotificationType.POST_LIKE, NotificationTargetType.POST, 1L));
        entityManager.flush();
    }

    @Test
    @DisplayName("차단이 없으면 알림이 보인다")
    void showsNotificationWhenNoBlock() {
        assertThat(findMine()).hasSize(1);
        assertThat(notificationRepository.countUnread(me.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("내가 차단한 사람의 알림은 빠진다")
    void hidesNotificationFromUserIBlocked() {
        blockRepository.insertIfAbsent(me.getId(), other.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(findMine()).isEmpty();
        assertThat(notificationRepository.countUnread(me.getId())).isZero();
    }

    @Test
    @DisplayName("나를 차단한 사람의 알림도 빠진다")
    void hidesNotificationFromUserWhoBlockedMe() {
        blockRepository.insertIfAbsent(other.getId(), me.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(findMine()).isEmpty();
        assertThat(notificationRepository.countUnread(me.getId())).isZero();
    }

    @Test
    @DisplayName("행동한 사람이 없는 알림은 차단과 무관하게 남는다")
    void keepsSystemNotification() {
        entityManager.persist(new Notification(
                me, null, NotificationType.POST_LIKE, NotificationTargetType.POST, 2L));
        blockRepository.insertIfAbsent(me.getId(), other.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(findMine()).singleElement()
                .satisfies(notification -> assertThat(notification.getActor()).isNull());
    }

    private List<Notification> findMine() {
        return notificationRepository.findMine(me.getId(), Long.MAX_VALUE, PageRequest.of(0, 20));
    }
}
