package com.server.post.repository;

import com.server.post.domain.PostMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

    List<PostMedia> findByPostId(Long postId);

    /** 피드 한 페이지의 미디어를 한 번에 읽어 게시물 수만큼 질의가 늘어나지 않게 한다. */
    List<PostMedia> findByPostIdIn(Collection<Long> postIds);

    /** 게시물을 완전히 지울 때 함께 지운다. */
    long deleteByPostId(Long postId);

    /**
     * 주어진 주소 중 실제로 게시물에 붙어 있는 것만 돌려준다.
     *
     * <p>저장소에 남은 파일이 쓰이고 있는지 확인하는 데 쓴다. 반대로 "안 붙은 것"을 묻지
     * 않는 이유는, 저장소에만 있고 DB 에는 없는 주소를 SQL 로 표현할 수 없어서다.
     */
    @Query("select m.url from PostMedia m where m.url in :urls")
    List<String> findUrlsIn(@Param("urls") Collection<String> urls);

    /** 게시물을 지우기 전에 딸린 파일 주소만 읽는다. 엔티티를 통째로 만들 이유가 없다. */
    @Query("select m.url from PostMedia m where m.post.id in :postIds")
    List<String> findUrlsByPostIdIn(@Param("postIds") Collection<Long> postIds);
}
