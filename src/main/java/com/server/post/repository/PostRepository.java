package com.server.post.repository;

import com.server.post.domain.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 최신순 피드 한 페이지. 첫 페이지는 {@code Long.MAX_VALUE}를 커서로 넘긴다.
     * 작성자는 응답에 항상 필요하므로 함께 조회한다.
     */
    @EntityGraph(attributePaths = "user")
    List<Post> findByDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);
}
