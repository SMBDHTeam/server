package com.server.block.controller;

import com.server.block.dto.BlockResponse;
import com.server.block.dto.BlockUserListResponse;
import com.server.block.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "커뮤니티 차단", description = "사용자 차단과 차단 목록")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/users/{userId}/blocks")
    @Operation(
            summary = "차단",
            description = "차단하면 서로의 팔로우가 끊기고 이 사용자의 게시물이 내 피드에서 사라진다. "
                    + "상대가 내 게시물을 보는 것은 막지 않는다."
    )
    public BlockResponse block(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "요청자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long requesterId,
            @Parameter(description = "차단할 대상 사용자 ID", example = "2") @PathVariable Long userId
    ) {
        return blockService.block(userId, requesterId);
    }

    @DeleteMapping("/users/{userId}/blocks")
    @Operation(
            summary = "차단 해제",
            description = "끊긴 팔로우는 되살아나지 않는다. 필요하면 다시 팔로우해야 한다."
    )
    public BlockResponse unblock(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "요청자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long requesterId,
            @Parameter(description = "차단을 풀 대상 사용자 ID", example = "2") @PathVariable Long userId
    ) {
        return blockService.unblock(userId, requesterId);
    }

    @GetMapping("/users/me/blocks")
    @Operation(summary = "내가 차단한 사용자 목록", description = "최근 차단한 순으로 반환한다.")
    public BlockUserListResponse getMyBlocks(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "요청자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "한 번에 가져올 인원 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        return blockService.getMyBlocks(userId, page, size);
    }
}
