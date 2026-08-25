package com.server.post.repository;

import com.server.post.domain.Comment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 게시물의 최상위 댓글 한 페이지. 오래된 순으로 읽으므로 첫 페이지는 커서에 0을 넘긴다.
     * 작성자는 응답에 항상 필요하므로 함께 조회한다.
     *
     * <p>삭제됐거나 작성자가 탈퇴한 댓글이라도 살아 있는 답글이 있으면 자리를 남긴다.
     * 그렇지 않으면 답글이 부모를 잃고 화면에서 사라진다. 자리만 남은 댓글은 응답에서
     * 작성자와 내용을 감춘다.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select comment from Comment comment
            where comment.post.id = :postId
              and comment.parent is null
              and comment.id > :cursor
              and ((comment.deletedAt is null and comment.user.deletedAt is null)
                   or exists (select 1 from Comment reply
                              where reply.parent = comment
                                and reply.deletedAt is null
                                and reply.user.deletedAt is null))
            order by comment.id asc
            """)
    List<Comment> findTopLevelComments(
            @Param("postId") Long postId, @Param("cursor") Long cursor, Pageable pageable);

    /** 부모 댓글 여러 건의 대댓글을 한 번에 읽는다. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select comment from Comment comment
            where comment.parent.id in :parentIds
              and comment.deletedAt is null
              and comment.user.deletedAt is null
            order by comment.id asc
            """)
    List<Comment> findRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /** 게시물 좋아요와 같은 이유로 DB 에서 직접 증감시킨다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Comment comment set comment.likeCount = comment.likeCount + 1 "
            + "where comment.id = :commentId")
    void increaseLikeCount(@Param("commentId") Long commentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Comment comment set comment.likeCount = comment.likeCount - 1 "
            + "where comment.id = :commentId and comment.likeCount > 0")
    void decreaseLikeCount(@Param("commentId") Long commentId);

    @Query("select comment.likeCount from Comment comment where comment.id = :commentId")
    int findLikeCountById(@Param("commentId") Long commentId);
}
