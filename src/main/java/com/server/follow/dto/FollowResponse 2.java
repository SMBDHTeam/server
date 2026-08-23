package com.server.follow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "팔로우 처리 결과")
public record FollowResponse(
        @Schema(description = "처리 후 대상 사용자의 팔로워 수", example = "152") long followerCount,
        @Schema(description = "요청한 사용자가 대상을 팔로우한 상태인지", example = "true")
        boolean following
) {
}
