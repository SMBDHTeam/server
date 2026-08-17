package com.server.post.dto;

import com.server.post.domain.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "댓글 한 건. 최상위 댓글이면 replies 에 답글이 담긴다.")
public record CommentResponse(
        @Schema(example = "3") Long id,
        PostDetailResponse.Author author,
        @Schema(example = "저도 여기 가봤는데 좋았어요") String content,
        @Schema(example = "2") int likeCount,
        @Schema(example = "2026-08-18T04:02:00") LocalDateTime createdAt,
        @Schema(description = "이 댓글에 달린 답글. 답글에는 다시 답글이 없으므로 항상 비어 있다.")
        List<CommentResponse> replies
) {

    public static CommentResponse from(Comment comment, List<CommentResponse> replies) {
        return new CommentResponse(
                comment.getId(),
                new PostDetailResponse.Author(
                        comment.getUser().getId(),
                        comment.getUser().getNickname(),
                        comment.getUser().getProfileImageUrl()),
                comment.getContent(),
                comment.getLikeCount(),
                comment.getCreatedAt(),
                replies);
    }

    public static CommentResponse from(Comment comment) {
        return from(comment, List.of());
    }
}
