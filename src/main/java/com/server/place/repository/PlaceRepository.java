package com.server.place.repository;

import com.server.place.domain.Place;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Override
    @EntityGraph(attributePaths = {"operatingInfo"})
    List<Place> findAll();

    @Override
    @EntityGraph(attributePaths = {"operatingInfo"})
    List<Place> findAllById(Iterable<Long> ids);

    List<Place> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    Optional<Place> findBySourceAndExternalContentId(String source, String externalContentId);

    @Override
    @EntityGraph(attributePaths = {"detail", "operatingInfo", "images"})
    Optional<Place> findById(Long id);

    /** 관리자가 가려 둔 장소. 최근에 가린 순이다. */
    List<Place> findByHiddenAtIsNotNullOrderByHiddenAtDesc();

    long countByHiddenAtIsNotNull();

    /**
     * 관리자 장소 검색.
     *
     * <p>가려 둔 장소를 <b>빼지 않는다.</b> 공개 검색은 가린 것을 걸러내지만, 관리 화면에서
     * 그러면 가린 장소를 다시 찾을 방법이 없어진다.
     *
     * <p>주소도 함께 본다. 공개 검색은 이름만 보는데, 관리자는 "해운대구 것들"처럼
     * 지역으로 찾는 일이 더 잦다.
     *
     * <p>{@code cast(:keyword as string)} 이 필요하다. 빼면 PostgreSQL 이 null 파라미터의
     * 타입을 정하지 못해 {@code lower(bytea)} 로 해석하고 500 이 난다. H2 는 통과하므로
     * 테스트만으로는 드러나지 않는다.
     */
    @Query("""
            select place from Place place
            where (:keyword is null
                   or lower(place.name) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(place.address) like lower(concat('%', cast(:keyword as string), '%')))
              and (:hidden is null
                   or (:hidden = true and place.hiddenAt is not null)
                   or (:hidden = false and place.hiddenAt is null))
            order by place.name asc
            """)
    List<Place> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("hidden") Boolean hidden,
            Pageable pageable);

    @Query("""
            select count(place) from Place place
            where (:keyword is null
                   or lower(place.name) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(place.address) like lower(concat('%', cast(:keyword as string), '%')))
              and (:hidden is null
                   or (:hidden = true and place.hiddenAt is not null)
                   or (:hidden = false and place.hiddenAt is null))
            """)
    long countForAdmin(@Param("keyword") String keyword, @Param("hidden") Boolean hidden);
}
