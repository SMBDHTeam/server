package com.server.post.repository;

import com.server.post.domain.Comment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 게시물의 최상위 댓글 한 페이지. 오래된 순으로 읽으므로 첫 페이지는 커서에 0을 넘긴다.
     * 작성자는 응답에 항상 필요하므로 함께 조회한다.
     */
    @EntityGraph(attributePaths = "user")
    List<Comment> findByPostIdAndParentIsNullAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
            Long postId, Long cursor, Pageable pageable);

    /** 부모 댓글 여러 건의 대댓글을 한 번에 읽는다. */
    @EntityGraph(attributePaths = "user")
    List<Comment> findByParentIdInAndDeletedAtIsNullOrderByIdAsc(Collection<Long> parentIds);
}
