package com.server.auth.web;

import com.server.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 인증이 필요한데 토큰이 없거나 유효하지 않을 때. */
@Component
public class ErrorResponseAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponder responder;

    public ErrorResponseAuthenticationEntryPoint(SecurityErrorResponder responder) {
        this.responder = responder;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        responder.write(request, response, ErrorCode.UNAUTHORIZED);
    }
}
