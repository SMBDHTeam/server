package com.server.post.repository;

import com.server.post.domain.PostLike;
import com.server.post.domain.PostLikeId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
