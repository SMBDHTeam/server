package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 관리자 사용자 검색을 실제 PostgreSQL 에서 확인한다.
 *
 * <p>검색어가 없을 때 PostgreSQL 은 파라미터 타입을 추론하지 못해
 * {@code function lower(bytea) does not exist} 로 실패한다. H2 는 이를 허용하므로
 * 기본 테스트로는 드러나지 않았고, dev 에서 500 으로 처음 발견했다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///tour_search_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@DisplayName("관리자 사용자 검색 (PostgreSQL)")
class AdminUserSearchIntegrationTest {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("검색어 없이 목록을 조회할 수 있다")
    void listsWithoutKeyword() {
        userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "pg-sub", "pg@example.com", "검색대상", null, UserRole.USER));

        var result = adminUserService.getUsers(null, null, 0, 20);

        assertThat(result.items()).isNotEmpty();
        assertThat(result.totalCount()).isPositive();
    }

    @Test
    @DisplayName("검색어가 있으면 닉네임과 이메일을 함께 찾는다")
    void searchesNicknameAndEmail() {
        userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "pg-sub-2", "finder@example.com", "찾을사람", null,
                UserRole.USER));

        assertThat(adminUserService.getUsers("찾을", null, 0, 20).items()).hasSize(1);
        assertThat(adminUserService.getUsers("finder@", null, 0, 20).items()).hasSize(1);
        assertThat(adminUserService.getUsers("없는값", null, 0, 20).items()).isEmpty();
    }
}
