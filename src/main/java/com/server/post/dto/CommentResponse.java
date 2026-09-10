package com.server.post.dto;

import com.server.common.support.RelativeTime;
import com.server.post.domain.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "댓글 한 건. 최상위 댓글이면 replies 에 답글이 담긴다.")
public record CommentResponse(
        @Schema(example = "3") Long id,
        @Schema(description = "감춰진 댓글이면 null 이다.")
        PostDetailResponse.Author author,
        @Schema(description = "감춰진 댓글이면 null 이다. 화면 문구는 클라이언트가 정한다.",
                example = "저도 여기 가봤는데 좋았어요")
        String content,
        @Schema(example = "2") int likeCount,
        @Schema(description = "요청자가 좋아요를 누른 상태인지. 요청자를 알 수 없으면 false 다.",
                example = "false")
        boolean liked,
        @Schema(example = "2026-08-18T04:02:00") LocalDateTime createdAt,
        @Schema(description = "작성 후 얼마나 지났는지. 화면에 그대로 쓰는 문구다.",
                example = "5분 전")
        String createdAgo,
        @Schema(description = "감춰진 댓글인지. 삭제됐거나 작성자가 탈퇴했지만 답글이 남아 "
                + "자리만 유지하는 경우 true 다.", example = "false")
        boolean deleted,
        @Schema(description = "감춰진 이유. deleted 가 false 면 null 이다. "
                + "화면 문구는 이 값을 보고 클라이언트가 정한다.", example = "WITHDRAWN")
        CommentHiddenReason hiddenReason,
        @Schema(description = "이 댓글에 달린 답글. 답글에는 다시 답글이 없으므로 항상 비어 있다.")
        List<CommentResponse> replies,

        @Schema(description = "이 댓글이 달린 뒤 게시물의 전체 댓글 수. 댓글·답글 작성 응답에만 "
                + "채워지고 목록 조회에서는 null 이다. 화면이 게시물을 다시 조회하지 않고 "
                + "숫자를 갱신할 수 있게 담는다.", example = "5")
        Integer postCommentCount
) {

    public static CommentResponse from(Comment comment, boolean liked, List<CommentResponse> replies) {
        CommentHiddenReason hiddenReason = hiddenReason(comment);
        if (hiddenReason != null) {
            // 답글을 매달 자리만 남기고 작성자와 내용을 감춘다.
            return new CommentResponse(
                    comment.getId(), null, null, 0, false, comment.getCreatedAt(),
                    RelativeTime.from(comment.getCreatedAt()),
                    true, hiddenReason, replies, null);
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
                RelativeTime.from(comment.getCreatedAt()),
                false,
                null,
                replies,
                null);
    }

    /** 댓글·답글 작성 응답. 게시물의 갱신된 댓글 수를 함께 담는다. */
    public static CommentResponse created(Comment comment, int postCommentCount) {
        CommentResponse response = from(comment, false);
        return new CommentResponse(
                response.id(),
                response.author(),
                response.content(),
                response.likeCount(),
                response.liked(),
                response.createdAt(),
                response.createdAgo(),
                response.deleted(),
                response.hiddenReason(),
                response.replies(),
                postCommentCount);
    }

    public static CommentResponse from(Comment comment, boolean liked) {
        return from(comment, liked, List.of());
    }

    /** 탈퇴한 사용자의 댓글도 삭제된 댓글과 똑같이 감춘다. 감출 이유가 없으면 null 이다. */
    private static CommentHiddenReason hiddenReason(Comment comment) {
        if (comment.getDeletedAt() != null) {
            return CommentHiddenReason.DELETED;
        }
        if (comment.getUser().getDeletedAt() != null) {
            return CommentHiddenReason.WITHDRAWN;
        }
        return null;
    }
}
