package com.server.post.repository;

import com.server.post.domain.PostPlaceTag;
import com.server.post.dto.PostPlaceTagView;
import java.util.Collection;
import java.util.List;
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
}
