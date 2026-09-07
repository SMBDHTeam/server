package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 관리자 장소 검색을 실제 PostgreSQL 에서 확인한다.
 *
 * <p>검색어가 없을 때 PostgreSQL 은 파라미터 타입을 추론하지 못해
 * {@code function lower(bytea) does not exist} 로 실패한다. 사용자 검색에서 같은 사고가
 * dev 500 으로 터졌다. H2 는 이를 허용하므로 기본 테스트로는 드러나지 않는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///tour_place_search_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@DisplayName("관리자 장소 검색 (PostgreSQL)")
class AdminPlaceSearchIntegrationTest {

    @Autowired
    private AdminPlaceService adminPlaceService;

    @Autowired
    private PlaceRepository placeRepository;

    private Place visible;
    private Place covered;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();
        visible = placeRepository.saveAndFlush(
                newPlace("검색노출장소", "부산광역시 해운대구 우동 1"));
        covered = placeRepository.saveAndFlush(
                newPlace("검색가림장소", "부산광역시 수영구 광안동 2"));
        adminPlaceService.updateHidden(covered.getId(), true, "좌표 오류");
    }

    private Place newPlace(String name, String address) {
        return new Place(
                "TOUR_API",
                name + "-external",
                "12",
                name,
                "관광지",
                address,
                new BigDecimal("129.16040000"),
                new BigDecimal("35.15870000"),
                null);
    }

    @Test
    @DisplayName("검색어 없이 목록을 조회할 수 있다")
    void listsWithoutKeyword() {
        var result = adminPlaceService.getPlaces(null, null, 0, 20);

        assertThat(result.items()).hasSize(2);
        assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("가려 둔 장소도 결과에 들어간다")
    void includesHiddenPlaces() {
        // 공개 검색은 가린 것을 뺀다. 관리 화면에서 그러면 되돌릴 대상을 찾을 수 없다.
        var ids = adminPlaceService.getPlaces(null, null, 0, 20).items().stream()
                .map(com.server.admin.dto.AdminPlaceResponse::id)
                .toList();

        assertThat(ids).contains(covered.getId());
    }

    @Test
    @DisplayName("주소로도 찾는다")
    void searchesByAddress() {
        // 공개 검색은 이름만 본다. 관리자는 지역으로 찾는 일이 더 잦다.
        var result = adminPlaceService.getPlaces("광안동", null, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(covered.getId());
    }

    @Test
    @DisplayName("이름으로 찾는다")
    void searchesByName() {
        assertThat(adminPlaceService.getPlaces("검색노출", null, 0, 20).items())
                .extracting(com.server.admin.dto.AdminPlaceResponse::id)
                .containsExactly(visible.getId());
    }

    @Test
    @DisplayName("hidden 으로 가린 것만 또는 노출 중인 것만 거를 수 있다")
    void filtersByHidden() {
        assertThat(adminPlaceService.getPlaces(null, true, 0, 20).items())
                .extracting(com.server.admin.dto.AdminPlaceResponse::id)
                .containsExactly(covered.getId());

        assertThat(adminPlaceService.getPlaces(null, false, 0, 20).items())
                .extracting(com.server.admin.dto.AdminPlaceResponse::id)
                .containsExactly(visible.getId());
    }

    @Test
    @DisplayName("페이지는 0부터 시작하고 전체 건수는 페이지와 무관하다")
    void paginates() {
        var first = adminPlaceService.getPlaces(null, null, 0, 1);
        var second = adminPlaceService.getPlaces(null, null, 1, 1);

        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).hasSize(1);
        assertThat(first.items().get(0).id())
                .isNotEqualTo(second.items().get(0).id());
        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(second.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("size 는 100 을 넘지 않는다")
    void capsPageSize() {
        // 관리자가 size=100000 을 넣어 전체를 한 번에 끌어오는 것을 막는다.
        assertThat(adminPlaceService.getPlaces(null, null, 0, 100000).items())
                .hasSizeLessThanOrEqualTo(100);
    }
}
