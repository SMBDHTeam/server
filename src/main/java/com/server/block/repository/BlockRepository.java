package com.server.block.repository;

import com.server.block.domain.Block;
import com.server.block.domain.BlockId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, BlockId> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    long deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @EntityGraph(attributePaths = "blocked")
    List<Block> findByBlockerIdOrderByCreatedAtDesc(Long blockerId, Pageable pageable);
}
