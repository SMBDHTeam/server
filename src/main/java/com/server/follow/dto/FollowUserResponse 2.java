package com.server.follow.dto;

import com.server.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팔로워·팔로잉 목록에 표시할 사용자")
public record FollowUserResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "감자") String nickname,
        @Schema(example = "https://example.com/profile/1.jpg") String profileImageUrl
) {

    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
