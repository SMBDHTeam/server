package com.server.post.repository;

import com.server.popularplace.dto.PopularPlaceView;
import com.server.post.domain.PostPlaceTag;
import com.server.post.dto.PostPlaceTagView;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostPlaceTagRepository extends JpaRepository<PostPlaceTag, Long> {

    // media 는 없을 수 있다. tag.media.id 로 바로 타고 들어가면 내부 조인이 걸려
    // 사진에 붙지 않은 예전 장소 태그가 조건과 무관하게 빠진다.

    @Query("""
            select new com.server.post.dto.PostPlaceTagView(
                tag.post.id, media.id, place.id, place.name)
            from PostPlaceTag tag
            join tag.place place
            left join tag.media media
            where tag.post.id = :postId
            """)
    List<PostPlaceTagView> findViewsByPostId(@Param("postId") Long postId);

    /** 피드 한 페이지의 장소 태그를 한 번에 읽는다. */
    @Query("""
            select new com.server.post.dto.PostPlaceTagView(
                tag.post.id, media.id, place.id, place.name)
            from PostPlaceTag tag
            join tag.place place
            left join tag.media media
            where tag.post.id in :postIds
            """)
    List<PostPlaceTagView> findViewsByPostIdIn(@Param("postIds") Collection<Long> postIds);

    /** 게시물을 완전히 지울 때 함께 지운다. */
    long deleteByPostId(Long postId);

    /**
     * 최근 커뮤니티에서 많이 언급된 장소. 홈 화면의 인기 여행지와 일정 만들 때 고르는
     * 화면에 쓴다. 관광 데이터에는 "요즘 사람들이 가는 곳" 같은 정보가 없으므로,
     * 사용자가 붙인 장소 태그로 그것을 만들어 낸다.
     *
     * <p>거르는 규칙은 카테고리가 가리키는 장소와 같다. <b>장소가 한 곳뿐인 글</b>만 세고,
     * <b>서로 다른 작성자 수</b>로 줄을 세운다. 한 사람이 같은 장소를 여러 번 태그해도
     * 순위가 오르지 않고, 혼자 붙인 것은 최소 인원에 걸려 목록에 뜨지 않는다.
     *
     * <p>언급한 사람 수와 글 수가 모두 같으면 <b>최근에 태그된 순</b>이다. 무작위로 섞으면
     * 새로고침할 때마다 순서가 바뀌어 사용자가 방금 본 장소를 다시 찾지 못한다. 지금처럼
     * 글이 적을 때는 대부분 동점이라 그 흔들림이 더 크게 보인다.
     *
     * <p>가려 둔 장소는 뺀다. 눌러서 상세로 들어갔을 때 없는 장소가 되고, 일정에 넣어도
     * 후보로 쓸 수 없다.
     *
     * @param since      이 시각 이후에 쓰인 글만 센다. "지금" 인기 있는 곳을 보여주기 위함이다
     * @param minAuthors 목록에 올리는 데 필요한 최소 인원
     */
    @Query("""
            select new com.server.popularplace.dto.PopularPlaceView(
                place.id, place.name, place.category, place.contentTypeId, place.address,
                place.latitude, place.longitude, place.primaryImageUrl,
                count(distinct post.id), count(distinct author.id),
                max(post.createdAt))
            from PostPlaceTag tag
            join tag.place place
            join tag.post post
            join post.user author
            where post.deletedAt is null
              and author.deletedAt is null
              and place.hiddenAt is null
              and post.createdAt >= :since
              and (select count(distinct other.place.id) from PostPlaceTag other
                   where other.post = post) = 1
            group by place.id, place.name, place.category, place.contentTypeId, place.address,
                     place.latitude, place.longitude, place.primaryImageUrl
            having count(distinct author.id) >= :minAuthors
            order by count(distinct author.id) desc,
                     count(distinct post.id) desc,
                     max(post.createdAt) desc
            """)
    List<PopularPlaceView> findPopularPlaces(
            @Param("since") LocalDateTime since,
            @Param("minAuthors") long minAuthors,
            Pageable pageable);
}
