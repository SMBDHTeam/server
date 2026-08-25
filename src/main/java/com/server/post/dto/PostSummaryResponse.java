package com.server.post.dto;

import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 피드 목록용 축약 응답. 미디어는 대표 한 장과 개수만 담는다.
 * 전체 미디어와 장소 태그가 필요하면 {@code GET /api/v1/posts/{postId}}를 사용한다.
 */
@Schema(description = "피드 목록용 축약 게시물")
public record PostSummaryResponse(
        @Schema(example = "7") Long id,
        PostDetailResponse.Author author,
        @Schema(example = "광안리 야경 보러 갔는데 날씨가 좋았어요") String content,
        @Schema(description = "sortOrder가 가장 앞선 미디어. 없으면 null이다.",
                example = "https://example.com/media/gwangalli-night.jpg")
        String thumbnailUrl,
        @Schema(description = "첨부 미디어 수. 2 이상이면 여러 장 표시를 띄운다.", example = "3")
        int mediaCount,
        @Schema(description = "대표 장소명. 태그가 없으면 null이다.", example = "광안리해수욕장")
        String placeName,
        @Schema(description = "본문에서 뽑은 해시태그. # 는 빼고 이름만 담는다.",
                example = "[\"광안리\", \"부산야경\"]")
        List<String> hashtags,
        @Schema(example = "12") int likeCount,
        @Schema(example = "3") int commentCount,
        @Schema(description = "요청자가 좋아요를 누른 상태인지. 요청자를 알 수 없으면 false 다.",
                example = "true")
        boolean liked,
        @Schema(description = "요청자가 저장한 상태인지. 요청자를 알 수 없으면 false 다.", example = "false")
        boolean bookmarked,
        @Schema(example = "2026-08-11T17:30:00") LocalDateTime createdAt
) {

    public static PostSummaryResponse from(
            Post post,
            List<PostMedia> mediaList,
            List<PostPlaceTagView> placeTags,
            List<String> hashtags,
            boolean liked,
            boolean bookmarked
    ) {
        String thumbnailUrl = mediaList.stream()
                .min(Comparator.comparingInt(PostMedia::getSortOrder))
                .map(PostMedia::getUrl)
                .orElse(null);
        String placeName = placeTags.stream()
                .findFirst()
                .map(PostPlaceTagView::placeName)
                .orElse(null);

        return new PostSummaryResponse(
                post.getId(),
                new PostDetailResponse.Author(
                        post.getUser().getId(),
                        post.getUser().getNickname(),
                        post.getUser().getProfileImageUrl()),
                post.getContent(),
                thumbnailUrl,
                mediaList.size(),
                placeName,
                hashtags,
                post.getLikeCount(),
                post.getCommentCount(),
                liked,
                bookmarked,
                post.getCreatedAt());
    }
}
