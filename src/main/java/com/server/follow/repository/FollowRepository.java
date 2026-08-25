package com.server.follow.repository;

import com.server.follow.domain.Follow;
import com.server.follow.domain.FollowId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long countByFollowingId(Long followingId);

    long countByFollowerId(Long followerId);

    /**
     * 대상을 팔로우하는 사람들. 목록에 닉네임을 담으므로 follower 를 함께 조회한다.
     *
     * <p>{@code follows}에는 대리키가 없어 커서 기준으로 삼을 단조 증가 값이 없다.
     * 목록 정확도 요구가 피드만큼 높지 않아 오프셋 페이징을 쓴다.
     */
    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFollowingIdOrderByCreatedAtDesc(Long followingId, Pageable pageable);

    /** 대상이 팔로우하는 사람들. */
    @EntityGraph(attributePaths = "following")
    List<Follow> findByFollowerIdOrderByCreatedAtDesc(Long followerId, Pageable pageable);

    /**
     * 있으면 그대로 두고 없을 때만 넣는다. 확인 후 저장하면 같은 요청이 동시에 들어올 때
     * 양쪽 다 없다고 읽어 기본키 충돌로 한쪽이 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into follows (follower_id, following_id, created_at)
            values (:followerId, :followingId, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("followerId") Long followerId, @Param("followingId") Long followingId);
}
