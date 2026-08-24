package com.server.post.repository;

import com.server.post.domain.CommentLike;
import com.server.post.domain.CommentLikeId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    long deleteByCommentIdAndUserId(Long commentId, Long userId);

    /** 댓글 목록에 좋아요 여부를 표시할 때, 댓글 수와 무관하게 한 번만 조회한다. */
    @Query("""
            select commentLike.comment.id from CommentLike commentLike
            where commentLike.user.id = :userId and commentLike.comment.id in :commentIds
            """)
    List<Long> findLikedCommentIds(
            @Param("userId") Long userId, @Param("commentIds") Collection<Long> commentIds);
}
