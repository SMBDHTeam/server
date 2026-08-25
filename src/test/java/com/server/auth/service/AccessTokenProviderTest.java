package com.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.server.auth.config.AuthProperties;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("액세스 토큰")
class AccessTokenProviderTest {

    private static final String SECRET = "test-secret-key-long-enough-for-hmac-256-0123456789";

    private AccessTokenProvider provider() {
        return provider(SECRET, Duration.ofMinutes(30));
    }

    private AccessTokenProvider provider(String secret, Duration accessTtl) {
        return new AccessTokenProvider(new AuthProperties(new AuthProperties.Google(List.of("client"), null, null), new AuthProperties.Jwt(secret, "busantour", accessTtl, null)));
    }

    private User user(Long id, UserRole role) {
        User user = User.ofOAuth(AuthProvider.GOOGLE, "sub", "a@example.com", "동준", null, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("발급한 토큰에서 사용자 ID와 권한을 되읽는다")
    void issuesAndParsesToken() {
        AccessTokenProvider provider = provider();

        AuthenticatedUser parsed = provider.parse(provider.issue(user(42L, UserRole.ADMIN)))
                .orElseThrow();

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.role()).isEqualTo(UserRole.ADMIN);
        assertThat(parsed.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰을 받아들이지 않는다")
    void rejectsTokenSignedWithAnotherSecret() {
        // 검증이 없으면 아무나 role=ADMIN 토큰을 만들어 관리자 API 를 부를 수 있다.
        String forged = JWT.create()
                .withIssuer("busantour")
                .withSubject("42")
                .withClaim("role", "ADMIN")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(600)))
                .sign(Algorithm.HMAC256("attacker-secret-key-long-enough-0123456789"));

        assertThat(provider().parse(forged)).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰을 받아들이지 않는다")
    void rejectsExpiredToken() {
        AccessTokenProvider expired = provider(SECRET, Duration.ofSeconds(-1));

        assertThat(provider().parse(expired.issue(user(42L, UserRole.USER)))).isEmpty();
    }

    @Test
    @DisplayName("발급자가 다른 토큰을 받아들이지 않는다")
    void rejectsTokenFromAnotherIssuer() {
        String other = JWT.create()
                .withIssuer("someone-else")
                .withSubject("42")
                .withClaim("role", "USER")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(600)))
                .sign(Algorithm.HMAC256(SECRET));

        assertThat(provider().parse(other)).isEmpty();
    }

    @Test
    @DisplayName("잘못된 토큰은 예외가 아니라 빈 값으로 다룬다")
    void returnsEmptyForGarbage() {
        // 인증이 필요 없는 경로에도 토큰이 실려 올 수 있다. 여기서 예외를 던지면
        // 비로그인도 볼 수 있어야 하는 화면이 깨진다.
        assertThat(provider().parse("not-a-token")).isEmpty();
        assertThat(provider().parse(null)).isEmpty();
        assertThat(provider().parse("  ")).isEmpty();
    }

    @Test
    @DisplayName("비밀키가 없으면 기동을 멈춘다")
    void refusesToStartWithoutSecret() {
        // 서명 키 없이 뜨면 누구나 원하는 사용자와 권한으로 토큰을 만들 수 있다.
        assertThatThrownBy(() -> provider(null, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> provider("   ", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("비밀키가 짧으면 기동을 멈춘다")
    void refusesToStartWithShortSecret() {
        assertThatThrownBy(() -> provider("too-short", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("짧다");
    }
}
