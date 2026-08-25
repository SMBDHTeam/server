package com.server.auth.web;

import com.server.auth.service.AccessTokenProvider;
import com.server.auth.service.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer} 로 온 액세스 토큰을 읽어 인증 정보를 채운다.
 *
 * <p><b>토큰을 요구하지 않는다.</b> 없거나 잘못돼도 그냥 통과시킨다. 어디에 인증이 필요한지는
 * {@code SecurityConfig} 가 정하며, 지금은 대부분 공개다. 인가를 켜는 것은 프론트 배포와
 * 맞춰야 하는 파괴적 변경이라 마지막에 한다.
 *
 * <p>덕분에 같은 코드로 선택적 인증이 된다. 비로그인도 피드를 보고, 토큰이 있으면 좋아요
 * 여부가 채워진다. 잘못된 토큰에 401 을 내면 이 동선이 깨진다.
 */
@Component
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final AccessTokenProvider accessTokenProvider;

    public AccessTokenAuthenticationFilter(AccessTokenProvider accessTokenProvider) {
        this.accessTokenProvider = accessTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 이미 인증돼 있으면 덮지 않는다.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolveToken(request)
                    .flatMap(accessTokenProvider::parse)
                    .ifPresent(user -> authenticate(request, user));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, AuthenticatedUser user) {
        var authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority(user.role().authority())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private java.util.Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(header.substring(PREFIX.length()).trim());
    }
}
