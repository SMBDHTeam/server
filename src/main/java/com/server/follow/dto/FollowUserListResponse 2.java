package com.server.follow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "팔로워 또는 팔로잉 목록. 최근 맺은 관계부터 반환한다.")
public record FollowUserListResponse(
        List<FollowUserResponse> items,
        @Schema(description = "전체 인원 수. 화면의 \"팔로워 152명\" 표시에 쓴다.", example = "152")
        long totalCount
) {
}
