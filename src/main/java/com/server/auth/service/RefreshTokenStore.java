package com.server.auth.service;

import com.server.auth.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰을 Redis 에 둔다.
 *
 * <p>키는 {@code refresh:{userId}:{해시}} 다. 사용자별로 묶여 있어 정지·탈퇴 때 한 번에
 * 지울 수 있다. 만료는 Redis TTL 이 처리하므로 정리 배치가 필요 없다.
 *
 * <p><b>원문을 저장하지 않는다.</b> SHA-256 해시만 넣는다. Redis 를 읽을 수 있는 사람이
 * 그대로 남의 세션을 이어받지 못하게 한다. 공유 링크 토큰과 같은 방식이다.
 *
 * <p><b>회전한다.</b> 갱신할 때마다 새 토큰을 주고 쓴 토큰은 지운다. 이미 지워진 토큰이
 * 다시 오면 탈취로 보고 그 사용자의 리프레시를 전부 폐기한다. 훔친 쪽과 원래 사용자가
 * 번갈아 갱신하면 반드시 한쪽이 지워진 토큰을 내밀게 되므로 탈취가 드러난다.
 */
@Component
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    private static final String KEY_PREFIX = "refresh:";

    /**
     * 회전으로 소비된 토큰의 흔적. 재사용과 단순 무효를 구분하기 위한 것이다.
     *
     * <p>이 표시가 없으면 만료됐거나 로그아웃한 토큰까지 탈취로 보게 된다. 로그아웃 직후
     * 클라이언트가 한 번만 재시도해도 다른 기기가 모두 끊기는 일이 생긴다.
     */
    private static final String USED_PREFIX = "refresh-used:";

    /**
     * 흔적을 남겨 두는 기간. 탈취된 토큰은 보통 곧바로 쓰이므로 길게 둘 필요가 없고,
     * 길수록 Redis 에 쌓인다.
     */
    private static final Duration USED_MARK_TTL = Duration.ofMinutes(10);

    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenStore(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.refreshTtl = properties.jwt().refreshTtl();
    }

    /** @return 클라이언트에게 줄 원문 토큰. 서버에는 해시만 남는다. */
    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redisTemplate.opsForValue().set(key(userId, token), "1", refreshTtl);
        return token;
    }

    /**
     * 토큰을 소비한다. 유효하면 지우고 참을 준다. 같은 토큰으로 두 번 성공할 수 없다.
     *
     * <p>삭제 결과로 판단하므로 동시에 같은 토큰이 들어와도 하나만 통과한다. 조회 후 삭제로
     * 나누면 둘 다 통과할 수 있다.
     */
    public boolean consume(Long userId, String token) {
        return consume(userId, token, true);
    }

    /**
     * @param markUsed 회전이면 {@code true}. 소비한 토큰의 흔적을 남겨 두었다가 다시 오면
     *                 탈취로 판정한다. 로그아웃이면 {@code false} 다. 로그아웃한 토큰이
     *                 다시 오는 것은 클라이언트 재시도일 뿐이라 다른 기기까지 끊을 이유가 없다.
     */
    public boolean consume(Long userId, String token, boolean markUsed) {
        if (userId == null || token == null || token.isBlank()) {
            return false;
        }
        boolean deleted = Boolean.TRUE.equals(redisTemplate.delete(key(userId, token)));
        if (deleted && markUsed) {
            redisTemplate.opsForValue().set(usedKey(userId, token), "1", USED_MARK_TTL);
        }
        return deleted;
    }

    /**
     * 회전으로 이미 소비된 토큰인지. 참이면 같은 토큰이 두 번 쓰인 것이므로 탈취로 본다.
     *
     * <p>거짓이면 만료·로그아웃·조작 중 하나다. 어느 쪽이든 이 요청만 거절하면 되고
     * 다른 기기를 끊을 근거는 되지 않는다.
     */
    public boolean wasRotated(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(usedKey(userId, token)));
    }

    /** 정지·탈퇴·탈취 의심 때 해당 사용자의 모든 기기를 끊는다. */
    public long revokeAll(Long userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long removed = redisTemplate.delete(keys);
        log.info("Revoked all refresh tokens. userId={}, count={}", userId, removed);
        return removed == null ? 0 : removed;
    }

    /**
     * 클라이언트가 보내는 리프레시 값은 {@code {userId}.{token}} 형태다.
     *
     * <p>토큰만 받으면 어느 사용자의 키를 지워야 할지 알 수 없고, 전체를 훑으면 사용자 수에
     * 비례해 느려진다. 사용자 ID 를 함께 받되 이는 조회용일 뿐이라 위조해도 남의 토큰을
     * 소비할 수 없다. 해시가 맞지 않으면 키가 없다.
     */
    public static Optional<RefreshTokenRef> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        int separator = value.indexOf('.');
        if (separator <= 0 || separator == value.length() - 1) {
            return Optional.empty();
        }
        try {
            Long userId = Long.valueOf(value.substring(0, separator));
            return Optional.of(new RefreshTokenRef(userId, value.substring(separator + 1)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public static String format(Long userId, String token) {
        return userId + "." + token;
    }

    public record RefreshTokenRef(Long userId, String token) {
    }

    private String key(Long userId, String token) {
        return KEY_PREFIX + userId + ":" + sha256(token);
    }

    private String usedKey(Long userId, String token) {
        return USED_PREFIX + userId + ":" + sha256(token);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
