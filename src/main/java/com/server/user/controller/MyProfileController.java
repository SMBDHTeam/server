package com.server.user.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.user.dto.NicknameUpdateRequest;
import com.server.user.dto.ProfileImageUpdateRequest;
import com.server.user.dto.UserProfileResponse;
import com.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청자 본인을 대상으로 하는 API. 경로 변수를 쓰는 {@link UserController}와 섞이지 않도록
 * {@code /users/me} 를 별도로 매핑한다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "커뮤니티 프로필", description = "사용자 프로필과 작성 게시물")
public class MyProfileController {

    private final UserService userService;

    public MyProfileController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/nickname")
    @Operation(
            summary = "닉네임 변경",
            description = "최대 10자다. 다른 사람이 쓰는 닉네임이면 409 를 반환한다. "
                    + "프로필 사진 변경은 별도 API 로 분리할 예정이다."
    )
    public UserProfileResponse changeNickname(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        Long userId = LoginUser.require(loginUser);
        return userService.changeNickname(userId, request);
    }

    @PatchMapping("/profile-image")
    @Operation(
            summary = "프로필 사진 변경",
            description = "이미 업로드된 URL 을 전달한다. 사진을 지우려면 DELETE 를 쓴다."
    )
    public UserProfileResponse changeProfileImage(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Valid @RequestBody ProfileImageUpdateRequest request
    ) {
        Long userId = LoginUser.require(loginUser);
        return userService.changeProfileImage(userId, request);
    }

    @DeleteMapping("/profile-image")
    @Operation(summary = "프로필 사진 제거", description = "기본 이미지 상태로 되돌린다.")
    public UserProfileResponse removeProfileImage(
            @AuthenticationPrincipal AuthenticatedUser loginUser
    ) {
        Long userId = LoginUser.require(loginUser);
        return userService.removeProfileImage(userId);
    }
}
