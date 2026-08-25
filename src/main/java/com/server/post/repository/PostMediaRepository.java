package com.server.post.repository;

import com.server.post.domain.PostMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

    List<PostMedia> findByPostId(Long postId);

    /** 피드 한 페이지의 미디어를 한 번에 읽어 게시물 수만큼 질의가 늘어나지 않게 한다. */
    List<PostMedia> findByPostIdIn(Collection<Long> postIds);

    /** 게시물을 완전히 지울 때 함께 지운다. */
    long deleteByPostId(Long postId);
}
