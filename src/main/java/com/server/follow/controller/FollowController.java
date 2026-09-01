package com.server.follow.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.follow.dto.FollowResponse;
import com.server.follow.dto.FollowUserListResponse;
import com.server.follow.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/{userId}")
@Tag(name = "커뮤니티 팔로우", description = "사용자 간 팔로우 관계")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/follows")
    @Operation(
            summary = "팔로우",
            description = "이미 팔로우한 상태에서 다시 요청해도 팔로워 수가 늘지 않는다. 자기 자신은 팔로우할 수 없다."
    )
    public FollowResponse follow(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(description = "팔로우할 대상 사용자 ID", example = "2") @PathVariable Long userId
    ) {
        Long requesterId = LoginUser.require(loginUser);
        return followService.follow(userId, requesterId);
    }

    @DeleteMapping("/follows")
    @Operation(
            summary = "팔로우 취소",
            description = "팔로우하지 않은 상태에서 요청해도 팔로워 수가 줄지 않는다."
    )
    public FollowResponse unfollow(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(description = "팔로우를 취소할 대상 사용자 ID", example = "2") @PathVariable Long userId
    ) {
        Long requesterId = LoginUser.require(loginUser);
        return followService.unfollow(userId, requesterId);
    }

    @GetMapping("/followers")
    @Operation(summary = "팔로워 목록", description = "이 사용자를 팔로우하는 사람들을 최근 순으로 반환한다.")
    public FollowUserListResponse getFollowers(
            @Parameter(example = "1") @PathVariable Long userId,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "한 번에 가져올 인원 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        return followService.getFollowers(userId, page, size);
    }

    @GetMapping("/followings")
    @Operation(summary = "팔로잉 목록", description = "이 사용자가 팔로우하는 사람들을 최근 순으로 반환한다.")
    public FollowUserListResponse getFollowings(
            @Parameter(example = "1") @PathVariable Long userId,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "한 번에 가져올 인원 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        return followService.getFollowings(userId, page, size);
    }
}
