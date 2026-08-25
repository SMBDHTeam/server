package com.server.hashtag.repository;

import com.server.hashtag.domain.PostHashtag;
import com.server.hashtag.domain.PostHashtagId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtagId> {

    @Query("select link.hashtag.id from PostHashtag link where link.post.id = :postId")
    List<Long> findHashtagIdsByPostId(@Param("postId") Long postId);

    long deleteByPostId(Long postId);

    /** 게시물 상세·목록에 태그 이름을 담을 때 쓴다. */
    @Query("""
            select link.post.id, link.hashtag.name from PostHashtag link
            where link.post.id in :postIds
            order by link.hashtag.name asc
            """)
    List<Object[]> findPostIdAndNamePairs(@Param("postIds") Collection<Long> postIds);
}
