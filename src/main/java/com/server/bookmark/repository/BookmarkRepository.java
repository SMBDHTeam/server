package com.server.bookmark.repository;

import com.server.bookmark.domain.Bookmark;
import com.server.bookmark.domain.BookmarkId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, BookmarkId> {

    boolean existsByUserIdAndPostId(Long userId, Long postId);

    /** 목록에 저장 여부를 표시할 때, 게시물 수와 무관하게 한 번만 조회한다. */
    @Query("""
            select bookmark.post.id from Bookmark bookmark
            where bookmark.user.id = :userId and bookmark.post.id in :postIds
            """)
    List<Long> findBookmarkedPostIds(
            @Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    long deleteByUserIdAndPostId(Long userId, Long postId);

    /**
     * 최근 저장한 순으로 읽는다. 저장 시각 기준 정렬이라 커서로 삼을 단조 증가 값이 없어
     * 오프셋 페이징을 쓴다. 이 목록은 본인만 바꾸므로 조회 중 목록이 밀릴 위험이 낮다.
     *
     * <p>삭제된 게시물과 탈퇴한 사용자의 게시물은 응답에서 제외해야 하므로 게시물과
     * 작성자를 함께 조회한다.
     */
    @EntityGraph(attributePaths = {"post", "post.user"})
    @Query("""
            select bookmark from Bookmark bookmark
            where bookmark.user.id = :userId
              and bookmark.post.deletedAt is null
              and bookmark.post.user.deletedAt is null
            order by bookmark.createdAt desc
            """)
    List<Bookmark> findReadableByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 있으면 그대로 두고 없을 때만 넣는다. 확인 후 저장하면 같은 요청이 동시에 들어올 때
     * 양쪽 다 없다고 읽어 기본키 충돌로 한쪽이 실패한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into bookmarks (user_id, post_id, created_at)
            values (:userId, :postId, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 게시물을 완전히 지울 때 함께 지운다. */
    long deleteByPostId(Long postId);
}
