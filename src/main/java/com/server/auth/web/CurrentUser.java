package com.server.auth.web;

import com.server.auth.service.AuthenticatedUser;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 지금 요청을 보낸 사용자.
 *
 * <p>인가를 전면 적용하기 전이라 로그인하지 않은 요청이 대부분이다. 그래서 없을 수 있는
 * 값으로 다룬다. 컨트롤러 파라미터로 주입하는 방식은 커뮤니티의 {@code X-User-Id} 를
 * 걷어낼 때 함께 정리한다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /** @return 로그인했으면 사용자 ID, 아니면 {@code null}. */
    public static Long idOrNull() {
        return get().map(AuthenticatedUser::id).orElse(null);
    }
}
