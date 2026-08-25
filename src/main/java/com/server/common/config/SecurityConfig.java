package com.server.common.config;

import com.server.auth.web.AccessTokenAuthenticationFilter;
import com.server.auth.web.ErrorResponseAccessDeniedHandler;
import com.server.auth.web.ErrorResponseAuthenticationEntryPoint;
import com.server.auth.web.SuspendedUserWriteFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

        // 사용자 API 의 인가는 아직 걸지 않는다. 비로그인 요청이 401 을 받기 시작하는
        // 파괴적 변경이라 프론트 배포와 맞춰야 한다.
        //
        // 관리자 경로만 예외다. /api/v1/** 를 통째로 permitAll 한 채 관리자 컨트롤러를 두면
        // 누구나 사용자를 정지시키고 게시물을 지울 수 있다. 만드는 시점부터 막는다.
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
