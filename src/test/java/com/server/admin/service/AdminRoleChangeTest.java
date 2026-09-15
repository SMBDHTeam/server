package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 역할 변경.
 *
 * <p>마지막 관리자가 내려가면 관리자 화면에 들어올 사람이 없고, 되돌리려면 운영 DB 에 직접
 * 붙어야 한다. 역할 변경 API 를 만든 이유가 그 일을 없애는 것이었다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 역할 변경")
class AdminRoleChangeTest {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = save("역할관리자", UserRole.ADMIN, "role-admin");
    }

    private User save(String nickname, UserRole role, String sub) {
        return userRepository.saveAndFlush(
                User.ofOAuth(AuthProvider.GOOGLE, sub, sub + "@example.com", nickname, null, role));
    }

    /**
     * 다른 테스트가 커밋해 둔 관리자가 있을 수 있어, 이 트랜잭션 안에서만 나머지를 내린다.
     * 테스트가 끝나면 되돌아간다.
     */
    private void leaveOnlyAdmin(User onlyAdmin) {
        userRepository.findAll().stream()
                .filter(User::isAdmin)
                .filter(user -> !user.getId().equals(onlyAdmin.getId()))
                .forEach(user -> user.changeRole(UserRole.USER));
        userRepository.flush();
    }

    @Test
    @DisplayName("마지막 관리자는 일반 사용자로 바꿀 수 없다")
    void rejectsDemotingLastAdmin() {
        leaveOnlyAdmin(admin);

        assertThatThrownBy(() -> adminUserService.changeRole(admin.getId(), UserRole.USER, admin.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_DEMOTE_LAST_ADMIN);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("다른 관리자가 남아 있으면 자기 자신도 내릴 수 있다")
    void allowsSelfDemotionWhenAnotherAdminRemains() {
        User other = save("다른관리자", UserRole.ADMIN, "role-admin-other");
        leaveOnlyAdmin(admin);
        other.changeRole(UserRole.ADMIN);
        userRepository.flush();

        adminUserService.changeRole(admin.getId(), UserRole.USER, admin.getId());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("마지막 관리자를 관리자로 다시 지정하는 것은 막지 않는다")
    void allowsKeepingLastAdminAsAdmin() {
        leaveOnlyAdmin(admin);

        adminUserService.changeRole(admin.getId(), UserRole.ADMIN, admin.getId());

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }
}
