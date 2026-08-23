package com.server.bookmark.repository;

import com.server.bookmark.domain.Bookmark;
import com.server.bookmark.domain.BookmarkId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, BookmarkId> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    long deleteByUserIdAndPostId(Long userId, Long postId);

    /**
     * 최근 저장한 순으로 읽는다. 저장 시각 기준 정렬이라 커서로 삼을 단조 증가 값이 없어
     * 오프셋 페이징을 쓴다. 이 목록은 본인만 바꾸므로 조회 중 목록이 밀릴 위험이 낮다.
     *
     * <p>삭제된 게시물은 응답에서 제외해야 하므로 게시물을 함께 조회한다.
     */
    @EntityGraph(attributePaths = {"post", "post.user"})
    List<Bookmark> findByUserIdAndPostDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId, Pageable pageable);
}
