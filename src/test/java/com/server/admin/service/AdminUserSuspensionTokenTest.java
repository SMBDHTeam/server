package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.auth.config.AuthProperties;
import com.server.auth.service.InMemoryStringRedisTemplate;
import com.server.auth.service.RefreshTokenStore;
import com.server.post.repository.PostRepository;
import com.server.report.repository.ReportRepository;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정지 시 리프레시 토큰 폐기.
 *
 * <p>살아 있는 Redis 를 요구하지 않도록 저장소를 대역으로 직접 구성한다. 검증하려는 것은
 * 정지와 폐기가 이어지는지이지 Redis 자체가 아니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("정지 시 토큰 폐기")
class AdminUserSuspensionTokenTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private ReportRepository reportRepository;

    private RefreshTokenStore store() {
        return new RefreshTokenStore(new InMemoryStringRedisTemplate(), new AuthProperties(
                new AuthProperties.Google(java.util.List.of("client"), null, null),
                new AuthProperties.Jwt("test-secret-key-long-enough-0123456789abcdef", null,
                        null, Duration.ofDays(14))));
    }

    @Test
    @DisplayName("정지하면 그 사용자의 모든 리프레시 토큰이 사라진다")
    void revokesEveryRefreshTokenOnSuspend() {
        // 액세스 토큰은 무상태라 남은 수명 동안 유효하다. 갱신을 막아야 그 뒤로 이어갈 수 없다.
        RefreshTokenStore store = store();
        AdminUserService service = new AdminUserService(
                userRepository, postRepository, reportRepository, store);
        User user = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "sub-x", "x@example.com", "정지대상", null, UserRole.USER));

        String phone = store.issue(user.getId());
        String laptop = store.issue(user.getId());

        service.updateStatus(user.getId(), true, 7, "광고성");

        assertThat(store.consume(user.getId(), phone)).isFalse();
        assertThat(store.consume(user.getId(), laptop)).isFalse();
    }

    @Test
    @DisplayName("토큰 폐기가 실패해도 정지는 유지된다")
    void keepsSuspensionWhenRevocationFails() {
        // Redis 가 죽었다고 악성 사용자를 정지시키지 못하면 곤란하다. 게다가 Redis 가 없으면
        // 갱신 자체가 실패하므로 폐기하지 못한 토큰으로도 세션을 이어갈 수 없다.
        RefreshTokenStore failing = new RefreshTokenStore(
                new InMemoryStringRedisTemplate() {
                    @Override
                    public java.util.Set<String> keys(String pattern) {
                        throw new IllegalStateException("Redis down");
                    }
                },
                new AuthProperties(
                        new AuthProperties.Google(java.util.List.of("client"), null, null),
                        new AuthProperties.Jwt("test-secret-key-long-enough-0123456789abcdef",
                                null, null, Duration.ofDays(14))));
        AdminUserService service = new AdminUserService(
                userRepository, postRepository, reportRepository, failing);
        User user = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "sub-y", "y@example.com", "정지대상2", null, UserRole.USER));

        var result = service.updateStatus(user.getId(), true, 7, "광고성");

        assertThat(result.writeBlocked()).isTrue();
    }
}
