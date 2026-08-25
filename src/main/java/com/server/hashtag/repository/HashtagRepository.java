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

    /**
     * 없을 때만 새 태그를 만든다. 같은 태그를 두 사람이 동시에 처음 쓰면 양쪽 다 없다고
     * 읽어 이름 고유 제약에 걸리므로, 확인 후 저장하지 않고 DB 에 맡긴다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into hashtags (name, post_count)
            values (:name, 0)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("name") String name);
}
