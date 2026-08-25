package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.domain.UserStatus;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 사용자 관리")
class AdminUserServiceTest {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;

    private User target;

    @BeforeEach
    void setUp() {
        target = save("여행자", UserRole.USER, "target-sub", "traveler@example.com");
    }

    private User save(String nickname, UserRole role, String sub, String email) {
        return userRepository.saveAndFlush(
                User.ofOAuth(AuthProvider.GOOGLE, sub, email, nickname, null, role));
    }

    @Test
    @DisplayName("정지하면 상태와 사유가 남고 쓰기가 막힌 것으로 표시된다")
    void suspendsUser() {
        var result = adminUserService.updateStatus(target.getId(), true, 7, "광고성 게시물 반복");

        assertThat(result.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(result.suspendedReason()).isEqualTo("광고성 게시물 반복");
        assertThat(result.suspendedUntil()).isNotNull();
        assertThat(result.writeBlocked()).isTrue();
    }

    @Test
    @DisplayName("기간을 생략하면 기한 없는 정지다")
    void suspendsIndefinitely() {
        var result = adminUserService.updateStatus(target.getId(), true, null, "심각한 위반");

        assertThat(result.suspendedUntil()).isNull();
        assertThat(result.writeBlocked()).isTrue();
    }

    @Test
    @DisplayName("기간이 지난 정지는 스스로 풀린다")
    void expiredSuspensionUnblocksItself() {
        // 상태를 되돌리는 배치가 없어도 만료가 동작해야 한다.
        target.suspend(LocalDateTime.now().minusDays(1), "지난 정지");
        userRepository.saveAndFlush(target);

        assertThat(adminUserService.getUser(target.getId()).user().writeBlocked()).isFalse();
    }

    @Test
    @DisplayName("해제하면 사유와 기한이 지워진다")
    void releasesSuspension() {
        adminUserService.updateStatus(target.getId(), true, 7, "광고성");

        var released = adminUserService.updateStatus(target.getId(), false, null, null);

        assertThat(released.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(released.suspendedUntil()).isNull();
        assertThat(released.suspendedReason()).isNull();
        assertThat(released.writeBlocked()).isFalse();
    }

    @Test
    @DisplayName("관리자는 정지할 수 없다")
    void cannotSuspendAdmin() {
        // 관리자끼리 서로 정지시키면 아무도 풀 수 없는 상태가 될 수 있고,
        // 그때 남는 수단은 DB 를 직접 고치는 것뿐이다.
        User admin = save("관리자", UserRole.ADMIN, "admin-sub", "admin@example.com");

        assertThatThrownBy(() -> adminUserService.updateStatus(admin.getId(), true, 7, "사유"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_SUSPEND_ADMIN);
    }

    @Test
    @DisplayName("닉네임과 이메일을 함께 검색한다")
    void searchesNicknameAndEmail() {
        save("다른사람", UserRole.USER, "other-sub", "someone@example.com");

        assertThat(adminUserService.getUsers("여행", null, 0, 20).items()).hasSize(1);
        assertThat(adminUserService.getUsers("traveler@", null, 0, 20).items()).hasSize(1);
        assertThat(adminUserService.getUsers("없는값", null, 0, 20).items()).isEmpty();
    }

    @Test
    @DisplayName("상태로 거르고 전체 건수를 함께 준다")
    void filtersByStatus() {
        save("정상", UserRole.USER, "active-sub", "active@example.com");
        adminUserService.updateStatus(target.getId(), true, 7, "사유");

        var suspended = adminUserService.getUsers(null, UserStatus.SUSPENDED, 0, 20);

        assertThat(suspended.items()).hasSize(1);
        assertThat(suspended.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 사용자도 상세로 조회된다")
    void findsWithdrawnUser() {
        // 신고를 따라 들어왔을 때 이미 탈퇴했다는 사실 자체가 필요한 정보다.
        target.delete(LocalDateTime.now());
        userRepository.saveAndFlush(target);

        var detail = adminUserService.getUser(target.getId());

        assertThat(detail.user().status()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(detail.user().deletedAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴한 사용자는 정지할 수 없다")
    void cannotSuspendWithdrawnUser() {
        target.delete(LocalDateTime.now());
        userRepository.saveAndFlush(target);

        assertThatThrownBy(() -> adminUserService.updateStatus(target.getId(), true, 7, "사유"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
