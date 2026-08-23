package com.server.user.dto;

import com.server.follow.dto.FollowUserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "닉네임 검색 결과. 팔로워·팔로잉 목록과 같은 형태다.")
public record UserSearchListResponse(
        List<FollowUserResponse> items
) {
}
