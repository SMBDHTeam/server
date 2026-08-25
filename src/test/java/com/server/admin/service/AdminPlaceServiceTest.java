package com.server.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.repository.PlaceRepository;
import com.server.place.service.PlaceService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("관리자 장소 관리")
class AdminPlaceServiceTest {

    @Autowired
    private AdminPlaceService adminPlaceService;
    @Autowired
    private PlaceService placeService;
    @Autowired
    private PlaceRepository placeRepository;

    private Place place;

    @BeforeEach
    void setUp() {
        place = placeRepository.saveAndFlush(newPlace("숨김테스트장소"));
    }

    private Place newPlace(String name) {
        return new Place(
                "TOUR_API",
                name + "-id",
                "12",
                name,
                "관광지",
                "부산광역시 해운대구 우동",
                new BigDecimal("129.16040000"),
                new BigDecimal("35.15870000"),
                null);
    }

    @Test
    @DisplayName("숨긴 장소는 이름 검색에서 빠진다")
    void hiddenPlaceDisappearsFromSearch() {
        PlaceSearchResponse before = placeService.search(
                "숨김테스트장소", null, null, null, "INTERNAL", 20);
        assertThat(before.items()).isNotEmpty();

        adminPlaceService.updateHidden(place.getId(), true, "좌표가 실제 위치와 다름");

        PlaceSearchResponse after = placeService.search(
                "숨김테스트장소", null, null, null, "INTERNAL", 20);
        assertThat(after.items()).isEmpty();
    }

    @Test
    @DisplayName("숨긴 장소는 상세 조회도 404 다")
    void hiddenPlaceIsNotVisibleInDetail() {
        // 검색에서만 빼고 상세를 열어 두면 예전 링크로 그대로 들어올 수 있다.
        adminPlaceService.updateHidden(place.getId(), true, "잘못된 정보");

        assertThatThrownBy(() -> placeService.getDetail(place.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("숨김을 풀면 다시 검색된다")
    void unhideRestoresSearch() {
        adminPlaceService.updateHidden(place.getId(), true, "잘못된 정보");
        adminPlaceService.updateHidden(place.getId(), false, null);

        assertThat(placeService.search("숨김테스트장소", null, null, null, "INTERNAL", 20).items())
                .isNotEmpty();
    }

    @Test
    @DisplayName("숨김 사유와 시각을 남긴다")
    void recordsHiddenReason() {
        var result = adminPlaceService.updateHidden(place.getId(), true, "좌표 오류");

        assertThat(result.hidden()).isTrue();
        assertThat(result.hiddenReason()).isEqualTo("좌표 오류");
        assertThat(result.hiddenAt()).isNotNull();
    }

    @Test
    @DisplayName("가려 둔 장소 목록에 나온다")
    void listsHiddenPlaces() {
        adminPlaceService.updateHidden(place.getId(), true, "좌표 오류");

        assertThat(adminPlaceService.getHiddenPlaces())
                .extracting(com.server.admin.dto.AdminPlaceResponse::id)
                .contains(place.getId());
    }

    @Test
    @DisplayName("없는 장소를 숨기려 하면 404 다")
    void rejectsUnknownPlace() {
        assertThatThrownBy(() -> adminPlaceService.updateHidden(999999L, true, "사유"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
    }
}
