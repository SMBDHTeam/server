package com.server.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정.
 *
 * <p>실제 값은 {@code .env.server} 에만 둔다. 기본값을 코드에 두는 것은 수명과 발급자처럼
 * 노출돼도 무해한 항목뿐이다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Google google, Jwt jwt) {

    /**
     * @param clientIds 허용할 구글 OAuth 클라이언트 ID. 쉼표로 여러 개를 넣을 수 있다.
     *                  웹·iOS·안드로이드는 각각 다른 클라이언트 ID 를 쓰므로, 하나만 두면
     *                  다른 플랫폼의 로그인이 전부 aud 검증에서 막힌다.
     *                  <b>프론트가 쓰는 값과 정확히 같아야 한다.</b>
     */
    public record Google(List<String> clientIds, String issuer, Duration jwksCacheTtl) {

        public Google {
            clientIds = clientIds == null ? List.of() : clientIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .toList();
            issuer = issuer == null || issuer.isBlank() ? "https://accounts.google.com" : issuer;
            jwksCacheTtl = jwksCacheTtl == null ? Duration.ofHours(6) : jwksCacheTtl;
        }
    }

    public record Jwt(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {

        public Jwt {
            issuer = issuer == null || issuer.isBlank() ? "busantour" : issuer;
            accessTtl = accessTtl == null ? Duration.ofMinutes(30) : accessTtl;
            refreshTtl = refreshTtl == null ? Duration.ofDays(14) : refreshTtl;
        }
    }
}
