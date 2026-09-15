package com.server.block.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.block.dto.BlockResponse;
import com.server.follow.service.FollowService;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import com.server.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단했다 풀면 화면에 다시 차단할 방법이 없었다. 프로필 응답이 차단 여부를 주지 않아,
 * 화면이 버튼을 누른 직후에만 상태를 알고 프로필을 다시 열면 잊어버렸기 때문이다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("차단 상태와 프로필")
class BlockProfileStateTest {

    @Autowired
    private BlockService blockService;

    @Autowired
    private FollowService followService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private Long meId;
    private Long targetId;

    @BeforeEach
    void setUp() {
        meId = userRepository.save(new User("차단하는사람" + System.nanoTime(), null)).getId();
        targetId = userRepository.save(new User("차단당하는사람" + System.nanoTime(), null)).getId();
    }

    @Test
    @DisplayName("차단하면 프로필에 blocked 가 true 로 나온다")
    void marksBlockedOnProfile() {
        blockService.block(targetId, meId);

        assertThat(userService.getProfile(targetId, meId).blocked()).isTrue();
    }

    @Test
    @DisplayName("차단을 풀면 다시 false 가 되어 차단 버튼을 보여줄 수 있다")
    void clearsBlockedAfterUnblock() {
        blockService.block(targetId, meId);

        blockService.unblock(targetId, meId);

        assertThat(userService.getProfile(targetId, meId).blocked()).isFalse();
    }

    @Test
    @DisplayName("차단한 적 없으면 false 이고, 비로그인도 false 다")
    void falseWhenNotBlocked() {
        assertThat(userService.getProfile(targetId, meId).blocked()).isFalse();
        assertThat(userService.getProfile(targetId, null).blocked()).isFalse();
    }

    @Test
    @DisplayName("차단 응답이 끊긴 팔로우 상태를 함께 알려준다")
    void reportsFollowingAfterBlock() {
        followService.follow(targetId, meId);

        BlockResponse blocked = blockService.block(targetId, meId);

        // 차단하면 서로의 팔로우가 끊긴다. 화면이 팔로우 버튼도 함께 되돌려야 한다.
        assertThat(blocked.blocked()).isTrue();
        assertThat(blocked.following()).isFalse();
    }

    @Test
    @DisplayName("차단을 풀어도 끊긴 팔로우는 되살아나지 않는다")
    void keepsFollowRemovedAfterUnblock() {
        followService.follow(targetId, meId);
        blockService.block(targetId, meId);

        BlockResponse unblocked = blockService.unblock(targetId, meId);

        assertThat(unblocked.blocked()).isFalse();
        assertThat(unblocked.following()).isFalse();
        assertThat(userService.getProfile(targetId, meId).following()).isFalse();
    }
}
