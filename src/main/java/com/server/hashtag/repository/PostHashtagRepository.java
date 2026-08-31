package com.server.hashtag.repository;

import com.server.hashtag.domain.PostHashtag;
import com.server.hashtag.domain.PostHashtagId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
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

    /**
     * 이 태그가 달린 게시물들이 가리키는 장소를, 언급한 사람 수가 많은 순으로 모은다.
     * "#맛집 이 붙은 글들이 실제로 어디를 말하는가"를 찾는 조회다.
     *
     * <p>카테고리는 장소가 아니라 글에 붙는다. 한 글에 장소가 여러 곳이면 그 카테고리가
     * 어느 곳을 가리키는지 알 수 없으므로, <b>장소가 한 곳뿐인 글</b>만 센다.
     * "해운대 갔다가 저녁에 국밥집" 같은 글이 두 곳 모두를 맛집으로 만들지 않게 한다.
     *
     * <p>또 <b>서로 다른 작성자 수</b>로 정렬하고 최소 인원을 요구한다. 한 사람이 같은
     * 장소에 여러 번 태그해도 순위가 오르지 않고, 혼자 장난친 것은 목록에 뜨지 않는다.
     *
     * <p>그래도 사용자가 만드는 데이터라 어긋난 것이 섞일 수 있다. 화면에서 확정된
     * 사실이 아니라 사용자들이 붙인 값임을 알 수 있게 보여줘야 한다.
     */
    @Query("""
            select place.id, place.name, place.category, place.address,
                   place.latitude, place.longitude,
                   count(distinct link.post.id), count(distinct link.post.user.id)
            from PostHashtag link
            join PostPlaceTag tag on tag.post = link.post
            join tag.place place
            where link.hashtag.name = :name
              and link.post.deletedAt is null
              and link.post.user.deletedAt is null
              and (select count(distinct other.place.id) from PostPlaceTag other
                   where other.post = link.post) = 1
            group by place.id, place.name, place.category, place.address,
                     place.latitude, place.longitude
            having count(distinct link.post.user.id) >= :minAuthors
            order by count(distinct link.post.user.id) desc, count(distinct link.post.id) desc
            """)
    List<Object[]> findPlacesByHashtag(
            @Param("name") String name,
            @Param("minAuthors") long minAuthors,
            Pageable pageable);
}
