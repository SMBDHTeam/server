package com.server.post.repository;

import com.server.post.domain.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    long countByUserIdAndDeletedAtIsNull(Long userId);

    /** 특정 사용자가 쓴 게시물 한 페이지. 피드와 같은 커서 방식을 쓴다. */
    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId, Long cursor, Pageable pageable);

    /**
     * 최신순 피드 한 페이지. 첫 페이지는 {@code Long.MAX_VALUE}를 커서로 넘긴다.
     * 작성자는 응답에 항상 필요하므로 함께 조회한다.
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findByDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

    @Query("select post.likeCount from Post post where post.id = :postId")
    int findLikeCountById(@Param("postId") Long postId);

    /**
     * 좋아요 수를 DB에서 직접 증감시킨다. 엔티티를 읽어 고쳐 쓰면 동시에 들어온 요청이
     * 같은 값을 읽어 하나가 사라질 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post post set post.likeCount = post.likeCount + 1 where post.id = :postId")
    void increaseLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post post set post.likeCount = post.likeCount - 1 "
            + "where post.id = :postId and post.likeCount > 0")
    void decreaseLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post post set post.commentCount = post.commentCount + 1 where post.id = :postId")
    void increaseCommentCount(@Param("postId") Long postId);
}
