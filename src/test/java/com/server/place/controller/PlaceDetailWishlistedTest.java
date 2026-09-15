package com.server.place.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.service.AccessTokenProvider;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import com.server.wishlist.service.PlaceWishlistService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 상세의 위시리스트 여부.
 *
 * <p>상세는 로그인 없이 열려 있다. 토큰이 없을 때 false 를 주면 "안 담았다"와 "모른다"가
 * 구분되지 않아 화면이 빈 하트를 그린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("장소 상세 위시리스트 여부")
class PlaceDetailWishlistedTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccessTokenProvider accessTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private PlaceWishlistService wishlistService;

    private User user;
    private Long placeId;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "wishlisted-sub", "wishlisted@example.com", "찜여부사용자", null, UserRole.USER));
        placeId = placeRepository.saveAndFlush(place("찜여부장소")).getId();
        token = "Bearer " + accessTokenProvider.issue(user);
    }

    @Test
    @DisplayName("로그인하지 않으면 null 이다")
    void nullWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/v1/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").doesNotExist());
    }

    @Test
    @DisplayName("로그인했고 담지 않았으면 false 다")
    void falseWhenNotWishlisted() throws Exception {
        mockMvc.perform(get("/api/v1/places/" + placeId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").value(false));
    }

    @Test
    @DisplayName("담았으면 true 다")
    void trueWhenWishlisted() throws Exception {
        wishlistService.add(placeId, user.getId());

        mockMvc.perform(get("/api/v1/places/" + placeId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").value(true));
    }

    private Place place(String name) {
        return new Place(
                "TOUR_API", "test-" + name, "12", name, "관광지", "부산 수영구",
                new BigDecimal("129.11860000"), new BigDecimal("35.15320000"), null);
    }
}
