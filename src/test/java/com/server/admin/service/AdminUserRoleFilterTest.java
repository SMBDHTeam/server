package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.admin.dto.AdminUserResponse;
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
 * 관리자 사용자 목록의 역할 필터.
 *
 * <p>운영자 화면이 관리자만 모아 본다. 필터가 없으면 전체 사용자를 받아 화면에서 걸러야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 사용자 목록 역할 필터")
class AdminUserRoleFilterTest {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;

    private User admin;
    private User user;

    @BeforeEach
    void setUp() {
        // 다른 테스트 데이터와 섞이지 않게 검색어로 좁힌다.
        admin = save("역할필터관리자", UserRole.ADMIN, "role-filter-admin");
        user = save("역할필터사용자", UserRole.USER, "role-filter-user");
    }

    private User save(String nickname, UserRole role, String sub) {
        return userRepository.saveAndFlush(
                User.ofOAuth(AuthProvider.GOOGLE, sub, sub + "@example.com", nickname, null, role));
    }

    @Test
    @DisplayName("ADMIN 으로 거르면 관리자만 나온다")
    void filtersAdmins() {
        var result = adminUserService.getUsers("역할필터", null, UserRole.ADMIN, 0, 20);

        assertThat(result.items()).extracting(AdminUserResponse::id).containsExactly(admin.getId());
        assertThat(result.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("USER 로 거르면 일반 사용자만 나온다")
    void filtersUsers() {
        var result = adminUserService.getUsers("역할필터", null, UserRole.USER, 0, 20);

        assertThat(result.items()).extracting(AdminUserResponse::id).containsExactly(user.getId());
        assertThat(result.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("역할을 생략하면 거르지 않는다")
    void noRoleMeansAll() {
        assertThat(adminUserService.getUsers("역할필터", null, null, 0, 20).totalCount()).isEqualTo(2);
    }
}
