package com.server.post.repository;

import com.server.post.domain.PostLike;
import com.server.post.domain.PostLikeId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /** 목록에 좋아요 여부를 표시할 때, 게시물 수와 무관하게 한 번만 조회한다. */
    @Query("""
            select postLike.post.id from PostLike postLike
            where postLike.user.id = :userId and postLike.post.id in :postIds
            """)
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    /** 취소된 건수를 돌려주므로 눌린 적 없는 좋아요를 취소해도 개수를 잘못 줄이지 않는다. */
    long deleteByPostIdAndUserId(Long postId, Long userId);

    /**
     * 있으면 그대로 두고 없을 때만 넣는다. 확인 후 저장하면 같은 요청이 동시에 들어올 때
     * 양쪽 다 없다고 읽어 기본키 충돌로 한쪽이 실패한다.
     *
     * @return 새로 넣은 행 수. 0 이면 이미 눌러 둔 것이라 좋아요 수를 올리지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into post_likes (post_id, user_id, created_at)
            values (:postId, :userId, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId, @Param("userId") Long userId);

    /** 게시물을 완전히 지울 때 함께 지운다. */
    long deleteByPostId(Long postId);
}
