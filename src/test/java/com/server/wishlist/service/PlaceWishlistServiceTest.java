package com.server.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import com.server.wishlist.dto.PlaceWishlistResponse;
import com.server.wishlist.repository.PlaceWishlistRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 담아 둔 장소는 일정을 만들 때 {@code mustVisitPlaceIds} 로 넘어간다. 목록이 주는
 * {@code placeId} 가 그대로 쓰이므로, 없는 장소나 가려진 장소가 섞이면 일정 생성이 깨진다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("장소 위시리스트")
class PlaceWishlistServiceTest {

    @Autowired
    private PlaceWishlistService wishlistService;

    @Autowired
    private PlaceWishlistRepository wishlistRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long placeId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("위시리스트사용자", null)).getId();
        placeId = placeRepository.save(place("광안리해수욕장")).getId();
    }

    @Test
    @DisplayName("담으면 목록에 나온다")
    void addsAndLists() {
        wishlistService.add(placeId, userId);

        assertThat(wishlistService.getMyWishlist(userId, 0, 20).items())
                .extracting(PlaceWishlistResponse::placeId, PlaceWishlistResponse::name)
                .containsExactly(org.assertj.core.api.Assertions.tuple(placeId, "광안리해수욕장"));
    }

    @Test
    @DisplayName("같은 장소를 두 번 담아도 한 줄이다")
    void addIsIdempotent() {
        wishlistService.add(placeId, userId);
        wishlistService.add(placeId, userId);

        assertThat(wishlistRepository.countByUserId(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("빼면 목록에서 사라진다")
    void removes() {
        wishlistService.add(placeId, userId);

        wishlistService.remove(placeId, userId);

        assertThat(wishlistService.getMyWishlist(userId, 0, 20).items()).isEmpty();
    }

    @Test
    @DisplayName("담지 않은 장소를 빼도 오류가 아니다")
    void removeIsIdempotent() {
        // 화면의 하트를 두 번 눌러도 같은 결과여야 한다.
        assertThatCode(() -> wishlistService.remove(placeId, userId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("없는 장소는 담을 수 없다")
    void rejectsUnknownPlace() {
        assertThatThrownBy(() -> wishlistService.add(999_999_999L, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("담은 뒤 가려진 장소는 목록에서 뺀다")
    void hidesHiddenPlace() {
        // 관리자가 숨긴 장소를 계속 보여주면, 눌러서 상세로 들어갔을 때 없는 장소가 된다.
        // 일정에 넣어도 후보로 쓸 수 없다.
        wishlistService.add(placeId, userId);
        Place place = placeRepository.findById(placeId).orElseThrow();
        place.hide("테스트");
        placeRepository.save(place);

        assertThat(wishlistService.getMyWishlist(userId, 0, 20).items()).isEmpty();
    }

    private Place place(String name) {
        return new Place(
                "TOUR_API", "test-" + name, "12", name, "관광지", "부산 수영구",
                new BigDecimal("129.11860000"), new BigDecimal("35.15320000"), null);
    }
}
