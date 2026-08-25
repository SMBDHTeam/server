package com.server.auth.web;

import com.server.common.error.ErrorCode;
import com.server.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 정지된 계정의 쓰기를 막는다.
 *
 * <p>정지 상태를 저장만 하고 확인하는 곳이 없으면 정지는 아무것도 막지 못한다. 관리자가
 * 정지시켜도 그 사용자는 계속 글을 쓴다.
 *
 * <p><b>쓰기 요청에서만 DB 를 읽는다.</b> 액세스 토큰에는 정지 여부가 없다. 매 요청 확인은
 * 모든 조회에 질의를 하나 더하는 것이라, 읽기는 그대로 두고 쓰기만 본다. 읽기를 막지 않는
 * 것은 의도이기도 하다. 정지된 사용자가 자기 상태를 확인할 수는 있어야 한다.
 *
 * <p>기한이 지난 정지는 스스로 풀린 것으로 본다({@code User.isWriteBlockedAt}). 상태를
 * 되돌리는 배치가 없어도 만료가 동작한다.
 */
@Component
public class SuspendedUserWriteFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SuspendedUserWriteFilter.class);

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PATCH", "PUT", "DELETE");

    /** 로그인·갱신은 막지 않는다. 정지된 사용자도 로그인해 자기 상태를 볼 수 있어야 한다. */
    private static final String AUTH_PATH = "/api/v1/auth";

    private final UserRepository userRepository;
    private final SecurityErrorResponder responder;

    public SuspendedUserWriteFilter(
            UserRepository userRepository, SecurityErrorResponder responder) {
        this.userRepository = userRepository;
        this.responder = responder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WRITE_METHODS.contains(request.getMethod())
                || request.getRequestURI().startsWith(AUTH_PATH);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Long userId = CurrentUser.idOrNull();
        if (userId == null) {
            // 로그인하지 않은 요청은 여기서 다루지 않는다. 인가가 판단할 몫이다.
            filterChain.doFilter(request, response);
            return;
        }

        boolean blocked = userRepository.findById(userId)
                .map(user -> user.isWriteBlockedAt(LocalDateTime.now()))
                .orElse(false);
        if (blocked) {
            log.info("Blocked write from suspended user. userId={}, uri={}",
                    userId, request.getRequestURI());
            responder.write(request, response, ErrorCode.USER_SUSPENDED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
