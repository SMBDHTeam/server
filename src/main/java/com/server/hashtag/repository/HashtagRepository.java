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
