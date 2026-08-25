package com.server.auth.service;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.server.auth.config.AuthProperties;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 구글 ID 토큰을 검증한다.
 *
 * <p>프론트가 구글 로그인으로 받은 토큰을 그대로 보내오므로, 우리 서버가 직접 확인하지 않으면
 * 아무나 만든 문자열로 로그인할 수 있다. 다음을 모두 본다.
 *
 * <ul>
 *   <li>서명 — 구글 JWKS 의 공개키로 검증한다</li>
 *   <li>{@code iss} — 구글이 발급했는가</li>
 *   <li>{@code aud} — <b>우리 앱</b>에 발급된 토큰인가. 이걸 빼면 다른 서비스용으로 발급된
 *       유효한 구글 토큰으로 우리 서비스에 로그인할 수 있다</li>
 *   <li>{@code exp} — 만료. {@link JWT#require} 가 함께 확인한다</li>
 *   <li>{@code email_verified} — 소유가 확인된 이메일인가</li>
 * </ul>
 *
 * <p>실패 사유는 로그에만 남기고 응답에는 담지 않는다. 어떤 검증에서 걸렸는지 알려 주면
 * 토큰을 조립해 보는 쪽에 단서가 된다.
 */
@Component
public class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    /** 구글은 두 형태를 모두 발급한다. 둘 다 정상이다. */
    private static final String ISSUER_WITHOUT_SCHEME = "accounts.google.com";

    /**
     * 시각 검증에 허용할 시계 오차.
     *
     * <p>이 값이 없으면 java-jwt 는 오차를 0초로 본다. 우리 시계가 구글보다 1초만 느려도
     * 방금 발급된 토큰의 {@code iat} 가 미래로 보여 "아직 쓸 수 없는 토큰"으로 거절되고,
     * <b>그 서버의 모든 로그인이 죽는다.</b> 시계가 몇 초 미끄러지는 것은 드문 일이 아니다.
     *
     * <p>{@code exp} 에도 같은 여유가 붙어 만료된 토큰이 60초 더 통한다. 구글 ID 토큰은
     * 한 시간을 살고 로그인 순간에 한 번만 쓰이므로, 이 정도는 값을 치를 만하다.
     * 구글 공식 자바 라이브러리도 같은 이유로 300초를 기본값으로 둔다.
     */
    private static final long CLOCK_SKEW_LEEWAY_SECONDS = 60;

    private final JwkProvider jwkProvider;
    private final AuthProperties.Google properties;

    public GoogleIdTokenVerifier(JwkProvider jwkProvider, AuthProperties properties) {
        this.jwkProvider = jwkProvider;
        this.properties = properties.google();

        if (this.properties.clientIds().isEmpty()) {
            // 비어 있으면 어떤 토큰도 aud 검증을 통과하지 못해 모든 로그인이 401 이 된다.
            // 안전한 방향이지만 로그만 봐서는 원인이 드러나지 않아 미리 알린다.
            log.warn("GOOGLE_CLIENT_ID 가 비어 있다. 구글 로그인이 전부 실패한다. "
                    + "프론트가 쓰는 클라이언트 ID 와 같은 값을 넣어야 한다.");
        }
    }

    public GoogleIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }

        DecodedJWT verified = verifySignatureAndClaims(idToken);
        requireVerifiedEmail(verified);

        return new GoogleIdentity(
                verified.getSubject(),
                text(verified, "email"),
                text(verified, "name"),
                text(verified, "picture"));
    }

    private DecodedJWT verifySignatureAndClaims(String idToken) {
        try {
            DecodedJWT decoded = JWT.decode(idToken);
            Jwk jwk = jwkProvider.get(decoded.getKeyId());
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

            return JWT.require(algorithm)
                    .withIssuer(properties.issuer(), ISSUER_WITHOUT_SCHEME)
                    .withAnyOfAudience(properties.clientIds().toArray(String[]::new))
                    .acceptLeeway(CLOCK_SKEW_LEEWAY_SECONDS)
                    .build()
                    .verify(idToken);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Google ID token verification failed. reason={}", exception.getMessage());
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
    }

    /**
     * 구글 계정에 붙어 있기만 하고 소유가 확인되지 않은 이메일은 거절한다. 확인되지 않은
     * 이메일을 그대로 믿으면 남의 이메일을 주장하는 계정이 관리자 승격 목록에 걸릴 수 있다.
     */
    private void requireVerifiedEmail(DecodedJWT verified) {
        Claim claim = verified.getClaim("email_verified");
        Boolean emailVerified = claim.isMissing() || claim.isNull() ? null : claim.asBoolean();
        if (!Boolean.TRUE.equals(emailVerified)) {
            log.warn("Google ID token rejected. reason=email not verified, subject={}",
                    verified.getSubject());
            throw new BusinessException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
    }

    private String text(DecodedJWT verified, String name) {
        Claim claim = verified.getClaim(name);
        return claim.isMissing() || claim.isNull() ? null : claim.asString();
    }
}
