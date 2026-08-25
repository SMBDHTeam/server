package com.server.block.repository;

import com.server.block.domain.Block;
import com.server.block.domain.BlockId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, BlockId> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    long deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /** 어느 쪽이 차단했든 두 사람 사이에 차단이 있는지 본다. */
    @Query("""
            select count(block) > 0 from Block block
            where (block.blocker.id = :oneId and block.blocked.id = :otherId)
               or (block.blocker.id = :otherId and block.blocked.id = :oneId)
            """)
    boolean existsBetween(@Param("oneId") Long oneId, @Param("otherId") Long otherId);

    @EntityGraph(attributePaths = "blocked")
    List<Block> findByBlockerIdOrderByCreatedAtDesc(Long blockerId, Pageable pageable);

    /**
     * 있으면 그대로 두고 없을 때만 넣는다. 확인 후 저장하면 같은 요청이 동시에 들어올 때
     * 양쪽 다 없다고 읽어 기본키 충돌로 한쪽이 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into blocks (blocker_id, blocked_id, created_at)
            values (:blockerId, :blockedId, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
}
