package com.server.post.dto;

import com.server.post.domain.MediaType;
import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Schema(description = "게시물 상세. 첨부 미디어와 장소 태그를 모두 포함한다.")
public record PostDetailResponse(
        @Schema(example = "7") Long id,
        Author author,
        @Schema(example = "광안리 야경 보러 갔는데 날씨가 좋았어요") String content,
        @Schema(description = "sortOrder 오름차순") List<Media> mediaList,
        List<PlaceTag> placeTags,
        @Schema(description = "이 게시물에 붙은 카테고리.", example = "[\"맛집\", \"야경\"]")
        List<String> categories,
        @Schema(example = "12") int likeCount,
        @Schema(example = "3") int commentCount,
        @Schema(description = "요청자가 좋아요를 누른 상태인지. 요청자를 알 수 없으면 false 다.",
                example = "true")
        boolean liked,
        @Schema(description = "요청자가 저장한 상태인지. 요청자를 알 수 없으면 false 다.", example = "false")
        boolean bookmarked,
        @Schema(example = "2026-08-11T17:30:00") LocalDateTime createdAt,
        @Schema(example = "2026-08-11T17:30:00") LocalDateTime updatedAt
) {

    @Schema(description = "작성자")
    public record Author(
            @Schema(example = "1") Long id,
            @Schema(example = "감자") String nickname,
            @Schema(example = "https://example.com/profile/1.jpg") String profileImageUrl
    ) {
    }

    /**
     * 응답용 미디어. 요청용 {@code PostCreateRequest.Media} 와 이름이 겹치면 OpenAPI
     * 스키마가 하나로 합쳐져, 응답에만 있는 {@code id} 가 문서에서 사라진다.
     */
    @Schema(name = "PostMedia", description = "첨부 미디어 한 건")
    public record Media(
            @Schema(example = "3") Long id,
            @Schema(example = "https://example.com/media/gwangalli-night.jpg") String url,
            @Schema(example = "IMAGE") MediaType mediaType,
            @Schema(example = "0") int sortOrder
    ) {
    }

    /**
     * 응답용 장소 태그. 요청용 {@code PostCreateRequest.PlaceTag} 와 이름이 겹치면
     * OpenAPI 스키마가 하나로 합쳐져, 응답에만 있는 {@code placeName} 이 문서에서 사라진다.
     */
    @Schema(name = "PostPlaceTag", description = "장소 태그 한 건")
    public record PlaceTag(
            @Schema(example = "42") Long placeId,
            @Schema(example = "광안리해수욕장") String placeName,
            @Schema(description = "사진이 촬영된 지점. 알 수 없으면 null이다.", example = "35.15320000")
            BigDecimal latitude,
            @Schema(example = "129.11860000") BigDecimal longitude
    ) {
    }

    public static PostDetailResponse from(
            Post post,
            List<PostMedia> mediaList,
            List<PostPlaceTagView> placeTags,
            List<String> categories,
            boolean liked,
            boolean bookmarked
    ) {
        return new PostDetailResponse(
                post.getId(),
                new Author(
                        post.getUser().getId(),
                        post.getUser().getNickname(),
                        post.getUser().getProfileImageUrl()),
                post.getContent(),
                mediaList.stream()
                        .sorted(Comparator.comparingInt(PostMedia::getSortOrder))
                        .map(media -> new Media(
                                media.getId(),
                                media.getUrl(),
                                media.getMediaType(),
                                media.getSortOrder()))
                        .toList(),
                placeTags.stream()
                        .map(tag -> new PlaceTag(
                                tag.placeId(),
                                tag.placeName(),
                                tag.latitude(),
                                tag.longitude()))
                        .toList(),
                categories,
                post.getLikeCount(),
                post.getCommentCount(),
                liked,
                bookmarked,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
