package com.server.user.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.post.dto.PostSummaryListResponse;
import com.server.post.service.PostService;
import com.server.user.dto.UserProfileResponse;
import com.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/{userId}")
@Tag(name = "커뮤니티 프로필", description = "사용자 프로필과 작성 게시물")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping("/profile")
    @Operation(
            summary = "프로필 조회",
            description = "게시물·팔로워·팔로잉 수와 요청자의 팔로우 여부를 함께 반환한다."
    )
    public UserProfileResponse getProfile(
            @Parameter(example = "1") @PathVariable Long userId,
            @AuthenticationPrincipal AuthenticatedUser loginUser
    ) {
        Long requesterId = LoginUser.require(loginUser);
        return userService.getProfile(userId, requesterId);
    }

    @GetMapping("/posts")
    @Operation(
            summary = "사용자 게시물 목록",
            description = "이 사용자가 쓴 게시물을 최신순으로 반환한다. 피드와 같은 커서 방식을 쓴다."
    )
    public PostSummaryListResponse getUserPosts(
            @Parameter(example = "1") @PathVariable Long userId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략한다.", example = "81")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 게시물 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size,
            @AuthenticationPrincipal AuthenticatedUser loginUser
    ) {
        Long requesterId = LoginUser.require(loginUser);
        return postService.getUserPosts(userId, cursor, size, requesterId);
    }
}
