package com.server.auth.config;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 구글 공개키 제공자.
 *
 * <p>토큰을 검증할 때마다 구글에 키를 물으면 로그인 지연이 그대로 늘고, 구글이 잠깐
 * 응답하지 않으면 로그인 전체가 멈춘다. 캐시하고 호출량을 제한한다.
 *
 * <p>키는 주기적으로 교체되므로 영구 캐시는 쓰지 않는다. 캐시가 만료되면 다음 요청에서
 * 다시 가져온다.
 */
@Configuration
public class GoogleJwkConfig {

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    /** 동시에 캐시할 키 수. 구글은 보통 2~3개를 노출한다. */
    private static final int CACHE_SIZE = 10;

    /** 캐시에 없는 kid 가 왔을 때의 조회 상한. 조작된 kid 로 외부 호출을 유발하는 것을 막는다. */
    private static final long LOOKUPS_PER_MINUTE = 10;

    @Bean
    JwkProvider googleJwkProvider(AuthProperties properties) throws MalformedURLException {
        return new JwkProviderBuilder(URI.create(GOOGLE_JWKS_URL).toURL())
                .cached(CACHE_SIZE, properties.google().jwksCacheTtl().toSeconds(), TimeUnit.SECONDS)
                .rateLimited(LOOKUPS_PER_MINUTE, 1, TimeUnit.MINUTES)
                .build();
    }
}
