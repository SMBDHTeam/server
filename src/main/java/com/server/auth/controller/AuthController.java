package com.server.auth.controller;

import com.server.auth.dto.AuthTokenResponse;
import com.server.auth.dto.GoogleLoginRequest;
import com.server.auth.dto.RefreshRequest;
import com.server.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "구글 로그인과 토큰 갱신")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    @Operation(
            summary = "구글 로그인",
            description = "프론트가 구글에서 받은 ID 토큰을 보내면 서명과 발급 대상을 검증하고 "
                    + "우리 서비스의 액세스·리프레시 토큰을 발급한다. 처음 로그인하는 계정은 이때 만들어진다."
    )
    public AuthTokenResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "액세스 토큰 갱신",
            description = "리프레시 토큰은 쓸 때마다 새 값으로 바뀐다. 응답의 refreshToken 을 보관하고 "
                    + "이전 값은 버린다. 이미 쓴 토큰을 다시 보내면 탈취로 보고 해당 사용자의 "
                    + "모든 기기 로그인을 끊는다."
    )
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "로그아웃",
            description = "보낸 리프레시 토큰만 폐기한다. 다른 기기의 로그인은 유지된다. "
                    + "액세스 토큰은 무상태라 남은 수명 동안 유효하므로 클라이언트가 함께 버려야 한다."
    )
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
