package com.server.post.repository;

import com.server.post.domain.Post;
import java.time.LocalDateTime;
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

    /**
     * 조회용 단건. 작성자가 탈퇴하면 게시물도 함께 사라져야 하므로 작성자 상태까지 본다.
     * 수정·삭제 경로는 요청자가 곧 작성자라 {@link #findByIdAndDeletedAtIsNull(Long)} 를 쓴다.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select post from Post post
            where post.id = :id
              and post.deletedAt is null
              and post.user.deletedAt is null
            """)
    Optional<Post> findReadableById(@Param("id") Long id);

    /**
     * 내가 지운 게시물 한 페이지. 휴지통 화면에서 쓰며 복구 기한이 남은 것만 준다.
     * 삭제 시각 기준 정렬이라 커서로 삼을 단조 증가 값이 없어 오프셋 페이징을 쓴다.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select post from Post post
            where post.user.id = :userId
              and post.deletedAt is not null
              and post.deletedAt >= :restorableFrom
            order by post.deletedAt desc
            """)
    List<Post> findDeletedByUserId(
            @Param("userId") Long userId,
            @Param("restorableFrom") LocalDateTime restorableFrom,
            Pageable pageable);

    /** 복구 기한이 지나 완전히 지울 게시물. 한 번에 지나치게 많이 잡지 않도록 나눠 읽는다. */
    @Query("""
            select post from Post post
            where post.deletedAt is not null
              and post.deletedAt < :deadline
            order by post.deletedAt asc
            """)
    List<Post> findDeletedBefore(
            @Param("deadline") LocalDateTime deadline, Pageable pageable);

    /** 복구 대상 단건. 삭제된 것만 찾으므로 살아 있는 글에는 복구가 걸리지 않는다. */
    @EntityGraph(attributePaths = "user")
    @Query("select post from Post post where post.id = :id and post.deletedAt is not null")
    Optional<Post> findDeletedById(@Param("id") Long id);

    /** 특정 사용자가 쓴 게시물 한 페이지. 피드와 같은 커서 방식을 쓴다. */
    @EntityGraph(attributePaths = "user")
    List<Post> findByUserIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
            Long userId, Long cursor, Pageable pageable);

    /**
     * 최신순 피드 한 페이지. 첫 페이지는 {@code Long.MAX_VALUE}를 커서로 넘긴다.
     * 작성자는 응답에 항상 필요하므로 함께 조회한다.
     *
     * @param followerId 값이 있으면 이 사용자가 팔로우한 사람들의 게시물만 남긴다.
     * @param placeId    값이 있으면 이 장소를 태그한 게시물만 남긴다.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select post from Post post
            where post.deletedAt is null
              and post.user.deletedAt is null
              and post.id < :cursor
              and (:followerId is null or post.user.id in (
                    select follow.following.id from Follow follow
                    where follow.follower.id = :followerId))
              and (:placeId is null or exists (
                    select 1 from PostPlaceTag tag
                    where tag.post = post and tag.place.id = :placeId))
              and (:hashtag is null or exists (
                    select 1 from PostHashtag link
                    where link.post = post and link.hashtag.name = :hashtag))
              and (:blockerId is null or not exists (
                    select 1 from Block block
                    where block.blocker.id = :blockerId and block.blocked = post.user))
            order by post.id desc
            """)
    List<Post> findFeed(
            @Param("followerId") Long followerId,
            @Param("placeId") Long placeId,
            @Param("hashtag") String hashtag,
            @Param("blockerId") Long blockerId,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * 탐색 탭용 인기 게시물. 점수는 좋아요 + 댓글×2 이며, 오래된 인기글이 상단을 계속
     * 차지하지 않도록 최근 게시물만 대상으로 한다.
     *
     * <p>점수 기준 정렬이라 커서로 삼을 단조 증가 값이 없어 오프셋 페이징을 쓴다.
     */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select post from Post post
            where post.deletedAt is null
              and post.user.deletedAt is null
              and post.createdAt >= :since
              and (:blockerId is null or not exists (
                    select 1 from Block block
                    where block.blocker.id = :blockerId and block.blocked = post.user))
            order by (post.likeCount + post.commentCount * 2) desc, post.id desc
            """)
    List<Post> findPopularFeed(
            @Param("since") LocalDateTime since,
            @Param("blockerId") Long blockerId,
            Pageable pageable);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post post set post.commentCount = post.commentCount - 1 "
            + "where post.id = :postId and post.commentCount > 0")
    void decreaseCommentCount(@Param("postId") Long postId);
}
