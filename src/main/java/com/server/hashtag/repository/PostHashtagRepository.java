package com.server.hashtag.repository;

import com.server.hashtag.domain.PostHashtag;
import com.server.hashtag.domain.PostHashtagId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtagId> {

    @Query("select link.hashtag.id from PostHashtag link where link.post.id = :postId")
    List<Long> findHashtagIdsByPostId(@Param("postId") Long postId);

    long deleteByPostId(Long postId);

    /**
     * 있으면 그대로 두고 없을 때만 넣는다. 게시물을 동시에 두 번 복구하거나 저장하면 두
     * 트랜잭션이 같은 (post_id, hashtag_id) 를 넣으려 해 늦은 쪽이 기본키 충돌로 실패한다.
     *
     * <p>태그 수만큼 반복 호출하므로 영속성 컨텍스트를 비우지 않는다. 다만 방금 만든
     * 해시태그 행이 먼저 반영돼야 외래키에 걸리지 않으므로 flush 는 필요하다.
     *
     * @return 새로 넣은 행 수. 0 이면 이미 연결돼 있어 사용 수를 올리지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into post_hashtags (post_id, hashtag_id)
            values (:postId, :hashtagId)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId, @Param("hashtagId") Long hashtagId);

    /** 게시물 상세·목록에 태그 이름을 담을 때 쓴다. */
    @Query("""
            select link.post.id, link.hashtag.name from PostHashtag link
            where link.post.id in :postIds
            order by link.hashtag.name asc
            """)
    List<Object[]> findPostIdAndNamePairs(@Param("postIds") Collection<Long> postIds);
}
