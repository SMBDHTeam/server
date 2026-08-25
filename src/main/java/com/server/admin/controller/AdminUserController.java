package com.server.admin.controller;

import com.server.admin.dto.AdminUserDetailResponse;
import com.server.admin.dto.AdminUserListResponse;
import com.server.admin.dto.AdminUserResponse;
import com.server.admin.dto.UserStatusUpdateRequest;
import com.server.admin.service.AdminUserService;
import com.server.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "관리자 - 사용자", description = "사용자 조회와 정지·해제")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(
            summary = "사용자 목록",
            description = "닉네임과 이메일을 함께 검색한다. 탈퇴한 사용자도 포함하며 "
                    + "status 로 거를 수 있다. 최근 가입한 순이다."
    )
    public AdminUserListResponse getUsers(
            @Parameter(description = "닉네임 또는 이메일 일부", example = "여행")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "ACTIVE, SUSPENDED, WITHDRAWN", example = "SUSPENDED")
            @RequestParam(required = false) UserStatus status,
            @Parameter(example = "0") @RequestParam(required = false) Integer page,
            @Parameter(example = "20") @RequestParam(required = false) Integer size
    ) {
        return adminUserService.getUsers(keyword, status, page, size);
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "사용자 상세",
            description = "조치를 판단할 수 있도록 게시물 수와 신고 이력 요약을 함께 준다. "
                    + "받은 신고 수는 사용자 직접 신고뿐 아니라 그가 쓴 게시물·댓글 신고도 센다."
    )
    public AdminUserDetailResponse getUser(
            @Parameter(example = "3") @PathVariable Long userId) {
        return adminUserService.getUser(userId);
    }

    @PatchMapping("/{userId}/status")
    @Operation(
            summary = "사용자 정지·해제",
            description = "정지하면 쓰기만 막고 읽기는 허용한다. 정지된 사용자가 자기 상태를 "
                    + "확인할 수는 있어야 한다. 정지 시 그 사용자의 리프레시 토큰을 모두 폐기하므로 "
                    + "액세스 토큰 수명이 끝나면 더 이상 이어갈 수 없다. days 를 생략하면 기한 없는 "
                    + "정지이며, 기한이 지난 정지는 스스로 풀린다. 관리자는 정지할 수 없다."
    )
    public AdminUserResponse updateStatus(
            @Parameter(example = "3") @PathVariable Long userId,
            @Valid @RequestBody UserStatusUpdateRequest request
    ) {
        return adminUserService.updateStatus(
                userId, request.suspended(), request.days(), request.reason());
    }
}
