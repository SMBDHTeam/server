package com.server.block.dto;

import com.server.follow.dto.FollowUserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내가 차단한 사용자 목록. 최근 차단한 순으로 반환한다.")
public record BlockUserListResponse(
        List<FollowUserResponse> items
) {
}
