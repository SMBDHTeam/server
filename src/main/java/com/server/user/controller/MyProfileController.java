package com.server.user.controller;

import com.server.user.dto.NicknameUpdateRequest;
import com.server.user.dto.UserProfileResponse;
import com.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "요청자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        return userService.changeNickname(userId, request);
    }
}
