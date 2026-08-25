package com.server.post.repository;

import com.server.post.domain.CommentLike;
import com.server.post.domain.CommentLikeId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 있으면 그대로 두고 없을 때만 넣는다. 확인 후 저장하면 같은 요청이 동시에 들어올 때
     * 양쪽 다 없다고 읽어 기본키 충돌로 한쪽이 실패한다.
     *
     * @return 새로 넣은 행 수. 0 이면 이미 눌러 둔 것이라 좋아요 수를 올리지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into comment_likes (comment_id, user_id, created_at)
            values (:commentId, :userId, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("commentId") Long commentId, @Param("userId") Long userId);
}
