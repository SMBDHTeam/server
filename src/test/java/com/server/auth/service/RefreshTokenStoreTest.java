package com.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.auth.config.AuthProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 리프레시 토큰 저장소.
 *
 * <p>살아 있는 Redis 를 요구하지 않도록 {@link StringRedisTemplate} 을 메모리 구현으로
 * 대신한다. 검증하려는 것은 회전과 재사용 판정이지 Redis 자체가 아니다.
 */
@DisplayName("리프레시 토큰 저장소")
class RefreshTokenStoreTest {

    private InMemoryStringRedisTemplate redis;
    private RefreshTokenStore store;

    @BeforeEach
    void setUp() {
        redis = new InMemoryStringRedisTemplate();
        store = new RefreshTokenStore(redis, new AuthProperties(
                new AuthProperties.Google(List.of("client"), null, null),
                new AuthProperties.Jwt("secret-long-enough-0123456789abcdef", null, null,
                        Duration.ofDays(14))));
    }

    @Test
    @DisplayName("발급한 토큰을 한 번 소비할 수 있다")
    void consumesIssuedTokenOnce() {
        String token = store.issue(42L);

        assertThat(store.consume(42L, token)).isTrue();
    }

    @Test
    @DisplayName("같은 토큰을 두 번 소비할 수 없다")
    void rejectsSecondUseOfSameToken() {
        // 회전의 핵심이다. 두 번 통과하면 훔친 토큰이 계속 살아 있다.
        String token = store.issue(42L);
        store.consume(42L, token);

        assertThat(store.consume(42L, token)).isFalse();
    }

    @Test
    @DisplayName("원문을 저장하지 않는다")
    void storesHashInsteadOfRawToken() {
        // Redis 를 읽을 수 있는 사람이 그대로 남의 세션을 이어받지 못하게 한다.
        String token = store.issue(42L);

        assertThat(redis.keys()).noneMatch(key -> key.contains(token));
        assertThat(redis.keys()).allMatch(key -> key.startsWith("refresh:42:"));
    }

    @Test
    @DisplayName("남의 사용자 ID 로는 토큰을 소비할 수 없다")
    void rejectsTokenUnderAnotherUserId() {
        String token = store.issue(42L);

        assertThat(store.consume(99L, token)).isFalse();
        assertThat(store.consume(42L, token)).isTrue();
    }

    @Test
    @DisplayName("한 사용자의 모든 토큰을 한 번에 폐기한다")
    void revokesEveryTokenOfUser() {
        // 정지·탈퇴·탈취 의심 때 모든 기기를 끊어야 한다.
        String first = store.issue(42L);
        String second = store.issue(42L);
        String other = store.issue(99L);

        assertThat(store.revokeAll(42L)).isEqualTo(2);
        assertThat(store.consume(42L, first)).isFalse();
        assertThat(store.consume(42L, second)).isFalse();
        assertThat(store.consume(99L, other)).isTrue();
    }

    @Test
    @DisplayName("클라이언트가 보내는 형식을 해석한다")
    void parsesClientFormat() {
        assertThat(RefreshTokenStore.parse("42.abc").orElseThrow().userId()).isEqualTo(42L);
        assertThat(RefreshTokenStore.parse("42.abc").orElseThrow().token()).isEqualTo("abc");
    }

    @Test
    @DisplayName("형식이 어긋난 값은 해석하지 않는다")
    void rejectsMalformedFormat() {
        assertThat(RefreshTokenStore.parse(null)).isEmpty();
        assertThat(RefreshTokenStore.parse("no-separator")).isEmpty();
        assertThat(RefreshTokenStore.parse(".abc")).isEmpty();
        assertThat(RefreshTokenStore.parse("42.")).isEmpty();
        assertThat(RefreshTokenStore.parse("abc.def")).isEmpty();
    }

    @Test
    @DisplayName("발급할 때마다 다른 토큰을 준다")
    void issuesDistinctTokens() {
        assertThat(store.issue(42L)).isNotEqualTo(store.issue(42L));
    }
}
