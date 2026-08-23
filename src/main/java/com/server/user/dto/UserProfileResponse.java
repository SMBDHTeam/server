package com.server.user.dto;

import com.server.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 화면 상단 정보")
public record UserProfileResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "감자") String nickname,
        @Schema(example = "https://example.com/profile/1.jpg") String profileImageUrl,
        @Schema(description = "삭제하지 않은 게시물 수", example = "12") long postCount,
        @Schema(example = "152") long followerCount,
        @Schema(example = "88") long followingCount,
        @Schema(description = "요청자가 이 사용자를 팔로우한 상태인지. 요청자를 알 수 없으면 false 다.",
                example = "true")
        boolean following,
        @Schema(description = "요청자 본인의 프로필인지. 본인이면 북마크 탭을 노출한다.", example = "false")
        boolean me
) {

    public static UserProfileResponse of(
            User user,
            long postCount,
            long followerCount,
            long followingCount,
            boolean following,
            boolean me
    ) {
        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                postCount,
                followerCount,
                followingCount,
                following,
                me);
    }
}
