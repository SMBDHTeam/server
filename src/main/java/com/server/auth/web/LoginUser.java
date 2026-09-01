package com.server.auth.web;

import com.server.auth.service.AuthenticatedUser;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;

/**
 * 컨트롤러가 {@code @AuthenticationPrincipal} 로 받은 값을 사용자 ID 로 바꾼다.
 *
 * <p>토큰이 없으면 principal 이 {@code null} 이다. 로그인이 필요한 경로는
 * {@link #require}, 없어도 되는 경로는 {@link #idOrNull} 을 쓴다. 둘을 구분하는 것은
 * 커뮤니티가 비로그인 조회를 허용하기 때문이다. 링크로 게시물에 바로 들어온 사람도
 * 글은 볼 수 있어야 하고, 그때 {@code liked} 같은 값만 비어 있으면 된다.
 *
 * <p>{@code SecurityConfig} 가 경로별로 한 번 더 막지만 여기서도 확인한다. 인가 규칙에서
 * 경로 하나가 빠지면 서비스가 {@code null} 을 사용자 ID 로 받아 조용히 잘못 동작한다.
 */
public final class LoginUser {

    private LoginUser() {
    }

    /** @throws BusinessException 로그인하지 않았으면 401 */
    public static Long require(AuthenticatedUser user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user.id();
    }

    /** @return 로그인했으면 사용자 ID, 아니면 {@code null} */
    public static Long idOrNull(AuthenticatedUser user) {
        return user == null ? null : user.id();
    }
}
