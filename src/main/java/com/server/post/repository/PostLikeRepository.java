package com.server.post.repository;

import com.server.post.domain.PostLike;
import com.server.post.domain.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /** 취소된 건수를 돌려주므로 눌린 적 없는 좋아요를 취소해도 개수를 잘못 줄이지 않는다. */
    long deleteByPostIdAndUserId(Long postId, Long userId);
}
