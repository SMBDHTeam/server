package com.server.auth.web;

import com.server.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 인증은 됐지만 권한이 모자랄 때. 관리자 경로에 일반 사용자가 접근한 경우가 대표적이다. */
@Component
public class ErrorResponseAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponder responder;

    public ErrorResponseAccessDeniedHandler(SecurityErrorResponder responder) {
        this.responder = responder;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        responder.write(request, response, ErrorCode.FORBIDDEN);
    }
}
