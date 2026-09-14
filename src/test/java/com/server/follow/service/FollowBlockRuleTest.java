package com.server.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.block.repository.BlockRepository;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.follow.repository.FollowRepository;
import com.server.notification.service.NotificationService;
import com.server.user.service.ActiveUserReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 차단은 팔로우를 끊는데 팔로우에는 차단 확인이 없어, 차단 직후 팔로우 한 번이면
 * 관계가 되살아났다. 방향에 따라 응답이 달라야 하므로 두 경우를 모두 고정한다.
 */
@DisplayName("차단과 팔로우")
class FollowBlockRuleTest {

    private static final long ME = 1L;
    private static final long TARGET = 2L;

    private final FollowRepository followRepository = Mockito.mock(FollowRepository.class);
    private final BlockRepository blockRepository = Mockito.mock(BlockRepository.class);
    private final ActiveUserReader activeUserReader = Mockito.mock(ActiveUserReader.class);
    private final NotificationService notificationService =
            Mockito.mock(NotificationService.class);

    private final FollowService followService =
            new FollowService(
                    followRepository, blockRepository, activeUserReader, notificationService);

    @Test
    @DisplayName("내가 차단한 상대는 차단을 풀어야 팔로우할 수 있다")
    void rejectsFollowingUserIBlocked() {
        givenActiveUsers();
        when(blockRepository.existsByBlockerIdAndBlockedId(ME, TARGET)).thenReturn(true);

        assertThatThrownBy(() -> followService.follow(TARGET, ME))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FOLLOW_BLOCKED_USER));
        verify(followRepository, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("나를 차단한 상대에게는 성공한 것처럼 응답하되 관계를 만들지 않는다")
    void hidesBlockFromTheBlockedUser() {
        givenActiveUsers();
        when(blockRepository.existsByBlockerIdAndBlockedId(ME, TARGET)).thenReturn(false);
        when(blockRepository.existsByBlockerIdAndBlockedId(TARGET, ME)).thenReturn(true);
        when(followRepository.countByFollowingId(TARGET)).thenReturn(0L);

        // 거절하면 상대가 차단당한 사실을 알게 되므로 오류를 내지 않는다.
        assertThat(followService.follow(TARGET, ME).following()).isTrue();

        verify(followRepository, never()).insertIfAbsent(anyLong(), anyLong());
    }

    @Test
    @DisplayName("차단이 없으면 평소대로 팔로우한다")
    void followsWhenNoBlockExists() {
        givenActiveUsers();
        when(blockRepository.existsByBlockerIdAndBlockedId(anyLong(), anyLong())).thenReturn(false);
        when(followRepository.countByFollowingId(TARGET)).thenReturn(1L);

        assertThat(followService.follow(TARGET, ME).followerCount()).isEqualTo(1);

        verify(followRepository).insertIfAbsent(ME, TARGET);
    }

    /** 살아 있는 사용자면 확인이 조용히 지나간다. mock 기본 동작이 그러하므로 둘 다 통과한다. */
    private void givenActiveUsers() {
        // ActiveUserReader 는 없는 사용자일 때만 예외를 던진다. 여기서는 막지 않는다.
    }
}
