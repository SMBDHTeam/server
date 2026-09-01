package com.server.hashtag.repository;

import com.server.hashtag.domain.Hashtag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    Optional<Hashtag> findByName(String name);

    List<Hashtag> findByNameIn(Collection<String> names);

    /** 자동완성. 많이 쓰인 태그를 먼저 보여준다. */
    List<Hashtag> findByNameStartingWithOrderByPostCountDescNameAsc(String prefix, Pageable pageable);

    /**
     * 등록된 태그 전체. 사용자가 새로 만들 수 없으므로 이 목록이 곧 선택지이자 화면의
     * 카테고리 탭이다. 지금은 여덟 개뿐이라 페이징하지 않는다.
     *
     * <p>이름순으로 준다. 사용 수로 정렬하면 글이 쌓일 때마다 탭 순서가 바뀌어,
     * 사용자가 늘 같은 자리에서 같은 탭을 누를 수 없다.
     */
    List<Hashtag> findAllByOrderByIdAsc();

    /**
     * 사용 수를 DB에서 직접 증감시킨다. 엔티티를 읽어 고쳐 쓰면 동시에 같은 태그를 쓴
     * 요청끼리 값이 어긋날 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Hashtag hashtag set hashtag.postCount = hashtag.postCount + 1 "
            + "where hashtag.id in :ids")
    void increasePostCount(@Param("ids") Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Hashtag hashtag set hashtag.postCount = hashtag.postCount - 1 "
            + "where hashtag.id in :ids and hashtag.postCount > 0")
    void decreasePostCount(@Param("ids") Collection<Long> ids);

}
