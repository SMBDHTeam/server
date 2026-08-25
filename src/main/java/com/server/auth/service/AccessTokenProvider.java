package com.server.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.server.auth.config.AuthProperties;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 자체 액세스 토큰을 발급하고 검증한다.
 *
 * <p>무상태다. 저장하지 않으므로 발급 후에는 취소할 수 없고, 수명이 다할 때까지 유효하다.
 * 그래서 수명을 짧게 두고 긴 수명은 리프레시 토큰이 맡는다.
 *
 * <p>{@code role} 을 함께 담아 매 요청마다 사용자를 조회하지 않는다. 대신 권한 변경이
 * 액세스 토큰 수명만큼 늦게 반영된다. 즉시 끊어야 하면 리프레시를 폐기해 갱신을 막는다.
 */
@Component
public class AccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenProvider.class);

    private static final String ROLE_CLAIM = "role";

    /** HMAC-SHA256 의 키 길이. 이보다 짧으면 서명 강도가 알고리즘 가정을 밑돈다. */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String issuer;
    private final Duration accessTtl;

    public AccessTokenProvider(AuthProperties properties) {
        AuthProperties.Jwt jwt = properties.jwt();
        this.algorithm = Algorithm.HMAC256(requireStrongSecret(jwt.secret()));
        this.issuer = jwt.issuer();
        this.accessTtl = jwt.accessTtl();
        this.verifier = JWT.require(algorithm).withIssuer(issuer).build();
    }

    /**
     * 비밀키가 없거나 짧으면 기동을 멈춘다.
     *
     * <p>비어 있으면 서명 없는 토큰이 되고, 짧으면 서명이 추측 가능해진다. 어느 쪽이든
     * 누구나 원하는 {@code sub} 와 {@code role} 로 토큰을 만들 수 있다. 운영에서 조용히
     * 뚫리는 것보다 뜨지 않는 편이 낫다.
     */
    private static byte[] requireStrongSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET 이 없다. 서명 키 없이는 토큰을 위조할 수 있다.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 이 너무 짧다. 최소 %d바이트가 필요하며 현재 %d바이트다."
                            .formatted(MINIMUM_SECRET_BYTES, bytes.length));
        }
        return bytes;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(String.valueOf(user.getId()))
                .withClaim(ROLE_CLAIM, user.getRole().name())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(accessTtl))
                .sign(algorithm);
    }

    /**
     * @return 유효하면 토큰이 가리키는 사용자, 아니면 비어 있음. 잘못된 토큰은 예외가 아니라
     *         빈 값으로 다룬다. 인증이 필요 없는 경로에도 토큰이 실려 올 수 있어서다.
     */
    public Optional<AuthenticatedUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            DecodedJWT decoded = verifier.verify(token);
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(decoded.getSubject()),
                    UserRole.valueOf(decoded.getClaim(ROLE_CLAIM).asString())));
        } catch (Exception exception) {
            log.debug("Access token rejected. reason={}", exception.getMessage());
            return Optional.empty();
        }
    }

    public Duration accessTtl() {
        return accessTtl;
    }
}
