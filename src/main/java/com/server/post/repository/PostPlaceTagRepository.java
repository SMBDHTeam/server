package com.server.post.repository;

import com.server.post.domain.PostPlaceTag;
import com.server.post.dto.PostPlaceTagView;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostPlaceTagRepository extends JpaRepository<PostPlaceTag, Long> {

    @Query("""
            select new com.server.post.dto.PostPlaceTagView(
                tag.post.id, place.id, place.name, tag.latitude, tag.longitude)
            from PostPlaceTag tag
            join tag.place place
            where tag.post.id = :postId
            """)
    List<PostPlaceTagView> findViewsByPostId(@Param("postId") Long postId);

    /** 피드 한 페이지의 장소 태그를 한 번에 읽는다. */
    @Query("""
            select new com.server.post.dto.PostPlaceTagView(
                tag.post.id, place.id, place.name, tag.latitude, tag.longitude)
            from PostPlaceTag tag
            join tag.place place
            where tag.post.id in :postIds
            """)
    List<PostPlaceTagView> findViewsByPostIdIn(@Param("postIds") Collection<Long> postIds);
}
