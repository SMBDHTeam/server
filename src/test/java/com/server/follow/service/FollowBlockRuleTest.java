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
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

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
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);

    private final FollowService followService =
            new FollowService(followRepository, blockRepository, userRepository);

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

    private void givenActiveUsers() {
        givenActiveUser(ME);
        givenActiveUser(TARGET);
    }

    private void givenActiveUser(long userId) {
        User user = new User("사용자" + userId, null);
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
    }
}
