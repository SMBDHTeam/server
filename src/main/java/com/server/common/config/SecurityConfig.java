package com.server.common.config;

import com.server.auth.web.AccessTokenAuthenticationFilter;
import com.server.auth.web.ErrorResponseAccessDeniedHandler;
import com.server.auth.web.ErrorResponseAuthenticationEntryPoint;
import com.server.auth.web.SuspendedUserWriteFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final AccessTokenAuthenticationFilter accessTokenAuthenticationFilter;
    private final ErrorResponseAuthenticationEntryPoint authenticationEntryPoint;
    private final ErrorResponseAccessDeniedHandler accessDeniedHandler;
    private final SuspendedUserWriteFilter suspendedUserWriteFilter;

    public SecurityConfig(
            CorsProperties corsProperties,
            AccessTokenAuthenticationFilter accessTokenAuthenticationFilter,
            ErrorResponseAuthenticationEntryPoint authenticationEntryPoint,
            ErrorResponseAccessDeniedHandler accessDeniedHandler,
            SuspendedUserWriteFilter suspendedUserWriteFilter
    ) {
        this.corsProperties = corsProperties;
        this.accessTokenAuthenticationFilter = accessTokenAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.suspendedUserWriteFilter = suspendedUserWriteFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()));
        // 토큰으로만 인증한다. 세션을 만들면 CSRF 를 끈 상태에서 쿠키 기반 공격면이 생긴다.
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 토큰이 있으면 읽어 인증 정보를 채운다. 없어도 통과시킨다.
        http.addFilterBefore(
                accessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // 인증 정보를 채운 뒤에 본다. 정지된 계정의 쓰기를 여기서 막지 않으면 정지가
        // 상태만 저장하고 아무것도 막지 못한다.
        http.addFilterAfter(suspendedUserWriteFilter, AccessTokenAuthenticationFilter.class);

        // 401·403 도 다른 오류와 같은 ErrorResponse 형태로 내보낸다. Security 예외는 필터
        // 단계에서 나 @ControllerAdvice 가 잡지 못하므로 여기서 직접 연결한다.
        http.exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        // 관리자 경로만 예외다. /api/v1/** 를 통째로 permitAll 한 채 관리자 컨트롤러를 두면
        // 누구나 사용자를 정지시키고 게시물을 지울 수 있다. 만드는 시점부터 막는다.
        //
        // 커뮤니티는 X-User-Id 를 걷어내면서 함께 걸었다. 공유 링크로 들어온 사람이 그 글
        // 하나는 볼 수 있게 열고, 나머지는 로그인을 요구한다.
        //
        // 일정 API 는 아직 걸지 않는다. 인증 없이 만든 일정이 남아 있어 사용자 범위를
        // 함께 정해야 한다.
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // 내 것을 읽는 경로. 조회지만 남의 것이 아니라 로그인이 필요하다.
                .requestMatchers("/api/v1/notifications/**").authenticated()
                .requestMatchers("/api/v1/users/me/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/me/deleted").authenticated()

                // 공유 링크로 들어온 사람이 그 글과 댓글까지는 볼 수 있어야 한다. 여기만
                // 열고 목록·프로필·검색은 막는다. 앱은 로그인해야 들어오는 구조라, 둘러보기는
                // 로그인한 사람의 몫이다.
                //
                // 열려 있어도 토큰을 보내면 읽는다. liked·bookmarked 는 로그인해야 채워진다.
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/posts/{postId:[0-9]+}",
                        "/api/v1/posts/{postId:[0-9]+}/comments",
                        "/api/v1/categories/**").permitAll()

                // 나머지 커뮤니티. 피드 목록과 프로필 조회도 여기 걸린다.
                .requestMatchers("/api/v1/posts/**", "/api/v1/users/**",
                        "/api/v1/media/**", "/api/v1/reports/**").authenticated()

                .requestMatchers("/api/v1/**", "/h2-console/**").permitAll()
                .anyRequest().permitAll()
        );
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(corsProperties.allowedHeaders());
        configuration.setExposedHeaders(corsProperties.exposedHeaders());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
    }
}
