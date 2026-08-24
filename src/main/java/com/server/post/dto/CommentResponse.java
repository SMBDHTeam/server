package com.server.post.dto;

import com.server.post.domain.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "댓글 한 건. 최상위 댓글이면 replies 에 답글이 담긴다.")
public record CommentResponse(
        @Schema(example = "3") Long id,
        @Schema(description = "삭제된 댓글이면 null 이다.")
        PostDetailResponse.Author author,
        @Schema(description = "삭제된 댓글이면 null 이다. 화면 문구는 클라이언트가 정한다.",
                example = "저도 여기 가봤는데 좋았어요")
        String content,
        @Schema(example = "2") int likeCount,
        @Schema(description = "요청자가 좋아요를 누른 상태인지. 요청자를 알 수 없으면 false 다.",
                example = "false")
        boolean liked,
        @Schema(example = "2026-08-18T04:02:00") LocalDateTime createdAt,
        @Schema(description = "삭제된 댓글인지. 답글이 남아 있어 자리만 유지하는 경우 true 다.",
                example = "false")
        boolean deleted,
        @Schema(description = "이 댓글에 달린 답글. 답글에는 다시 답글이 없으므로 항상 비어 있다.")
        List<CommentResponse> replies
) {

    public static CommentResponse from(Comment comment, boolean liked, List<CommentResponse> replies) {
        if (comment.getDeletedAt() != null) {
            // 삭제된 댓글은 답글을 매달 자리만 남기고 작성자와 내용을 감춘다.
            return new CommentResponse(
                    comment.getId(), null, null, 0, false, comment.getCreatedAt(), true, replies);
        }
        return new CommentResponse(
                comment.getId(),
                new PostDetailResponse.Author(
                        comment.getUser().getId(),
                        comment.getUser().getNickname(),
                        comment.getUser().getProfileImageUrl()),
                comment.getContent(),
                comment.getLikeCount(),
                liked,
                comment.getCreatedAt(),
                false,
                replies);
    }

    public static CommentResponse from(Comment comment, boolean liked) {
        return from(comment, liked, List.of());
    }
}
