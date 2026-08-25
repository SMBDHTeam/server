package com.server.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.server.auth.config.AuthProperties;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 RSA 키로 토큰을 만들어 검증한다. 구글을 호출하지 않는다.
 *
 * <p>검증기는 로그인의 유일한 관문이라, 각 검사가 실제로 걸러 내는지 하나씩 확인한다.
 */
@DisplayName("구글 ID 토큰 검증")
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "our-app.apps.googleusercontent.com";
    private static final String KEY_ID = "test-key";
    private static final String ISSUER = "https://accounts.google.com";

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static RSAPublicKey otherPublicKey;
    private static RSAPrivateKey otherPrivateKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair pair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();

        KeyPair other = generator.generateKeyPair();
        otherPublicKey = (RSAPublicKey) other.getPublic();
        otherPrivateKey = (RSAPrivateKey) other.getPrivate();
    }

    private GoogleIdTokenVerifier verifier() {
        return verifier(publicKey);
    }

    /** 구글이 노출한다고 가정할 공개키를 지정한다. */
    private GoogleIdTokenVerifier verifier(RSAPublicKey served) {
        JwkProvider provider = keyId -> new Jwk(
                keyId, "RSA", "RS256", null, List.of(), null, null, null,
                Map.of("n", base64Url(served.getModulus().toByteArray()),
                        "e", base64Url(served.getPublicExponent().toByteArray())));

        AuthProperties properties = new AuthProperties(
                new AuthProperties.Google(List.of(CLIENT_ID), ISSUER, Duration.ofHours(6)),
                new AuthProperties.Jwt("secret", "busantour", null, null));
        return new GoogleIdTokenVerifier(provider, properties);
    }

    private static String base64Url(byte[] bytes) {
        int start = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        byte[] trimmed = new byte[bytes.length - start];
        System.arraycopy(bytes, start, trimmed, 0, trimmed.length);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
    }

    private JWTBuilderFixture token() {
        return new JWTBuilderFixture();
    }

    /** 기본값은 유효한 토큰이고, 테스트마다 한 가지만 어긋나게 만든다. */
    private static class JWTBuilderFixture {
        private String issuer = ISSUER;
        private String audience = CLIENT_ID;
        private Object emailVerified = true;
        private Instant expiresAt = Instant.now().plusSeconds(600);
        private Instant issuedAt = null;
        private RSAPrivateKey signingKey = privateKey;

        JWTBuilderFixture issuer(String value) {
            this.issuer = value;
            return this;
        }

        JWTBuilderFixture audience(String value) {
            this.audience = value;
            return this;
        }

        JWTBuilderFixture emailVerified(Object value) {
            this.emailVerified = value;
            return this;
        }

        JWTBuilderFixture expiresAt(Instant value) {
            this.expiresAt = value;
            return this;
        }

        JWTBuilderFixture issuedAt(Instant value) {
            this.issuedAt = value;
            return this;
        }

        JWTBuilderFixture signedWith(RSAPrivateKey value) {
            this.signingKey = value;
            return this;
        }

        String build() {
            var builder = JWT.create()
                    .withKeyId(KEY_ID)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .withSubject("google-sub-1")
                    .withClaim("email", "traveler@example.com")
                    .withClaim("name", "여행자")
                    .withClaim("picture", "https://example.com/p.png")
                    .withExpiresAt(Date.from(expiresAt));
            if (issuedAt != null) {
                builder.withIssuedAt(Date.from(issuedAt));
            }
            if (emailVerified instanceof Boolean value) {
                builder.withClaim("email_verified", value);
            } else if (emailVerified instanceof String value) {
                builder.withClaim("email_verified", value);
            }
            return builder.sign(Algorithm.RSA256(null, signingKey));
        }
    }

    @Test
    @DisplayName("유효한 토큰에서 sub·이메일·이름·사진을 뽑는다")
    void extractsIdentityFromValidToken() {
        GoogleIdentity identity = verifier().verify(token().build());

        assertThat(identity.subject()).isEqualTo("google-sub-1");
        assertThat(identity.email()).isEqualTo("traveler@example.com");
        assertThat(identity.name()).isEqualTo("여행자");
        assertThat(identity.pictureUrl()).isEqualTo("https://example.com/p.png");
    }

    @Test
    @DisplayName("다른 앱에 발급된 토큰을 거절한다")
    void rejectsTokenIssuedForAnotherAudience() {
        // aud 를 확인하지 않으면 다른 서비스용으로 발급된 유효한 구글 토큰으로 로그인된다.
        assertThatThrownBy(() -> verifier().verify(token().audience("other-app").build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("구글이 발급하지 않은 토큰을 거절한다")
    void rejectsTokenFromAnotherIssuer() {
        assertThatThrownBy(() -> verifier().verify(token().issuer("https://evil.example").build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("scheme 없는 issuer 도 허용한다")
    void acceptsIssuerWithoutScheme() {
        // 구글은 accounts.google.com 형태로도 발급한다. 둘 다 정상이다.
        assertThat(verifier().verify(token().issuer("accounts.google.com").build()).subject())
                .isEqualTo("google-sub-1");
    }

    @Test
    @DisplayName("서명이 맞지 않는 토큰을 거절한다")
    void rejectsTokenWithWrongSignature() {
        // 구글이 노출하는 키는 publicKey 인데 토큰은 다른 키로 서명됐다.
        assertThatThrownBy(() -> verifier().verify(token().signedWith(otherPrivateKey).build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
        assertThat(otherPublicKey).isNotEqualTo(publicKey);
    }

    @Test
    @DisplayName("만료된 토큰을 거절한다")
    // 허용 오차 60초 밖으로 확실히 밀어 둔다. 10초 전 만료로 두면 오차 안에 들어와
    // 통과해 버리고, 만료 검사가 사라져도 이 테스트는 초록으로 남는다.
    void rejectsExpiredToken() {
        assertThatThrownBy(() -> verifier()
                .verify(token().expiresAt(Instant.now().minusSeconds(600)).build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("우리 시계가 몇 초 느려도 방금 발급된 토큰을 받는다")
    void acceptsTokenIssuedSlightlyInTheFuture() {
        // 서버 시계가 구글보다 느리면 방금 발급된 토큰의 iat 가 미래로 보인다. 허용 오차가
        // 없으면 여기서 전부 막혀 그 서버의 로그인이 통째로 죽는다. 실제로 로컬에서
        // 2초 차이만으로 모든 로그인이 401 이 됐다.
        String token = token().issuedAt(Instant.now().plusSeconds(30)).build();

        assertThat(verifier().verify(token).subject()).isEqualTo("google-sub-1");
    }

    @Test
    @DisplayName("발급 시각이 한참 미래인 토큰은 거절한다")
    void rejectsTokenIssuedFarInTheFuture() {
        // 오차를 허용한다고 해서 아무 시각이나 받는 것은 아니다.
        assertThatThrownBy(() -> verifier()
                .verify(token().issuedAt(Instant.now().plusSeconds(600)).build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("이메일이 확인되지 않은 계정을 거절한다")
    void rejectsUnverifiedEmail() {
        assertThatThrownBy(() -> verifier().verify(token().emailVerified(false).build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("email_verified 가 아예 없으면 거절한다")
    void rejectsMissingEmailVerifiedClaim() {
        // 없는 것을 참으로 보면 확인되지 않은 이메일이 그대로 통과한다.
        assertThatThrownBy(() -> verifier().verify(token().emailVerified(null).build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("비어 있는 토큰을 거절한다")
    void rejectsBlankToken() {
        assertThatThrownBy(() -> verifier().verify("  "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_GOOGLE_TOKEN);
    }
}
