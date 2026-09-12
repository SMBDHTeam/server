package com.server.wishlist.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 위시리스트는 장소 경로 아래에 있다. 장소 조회는 인증 없이 열려 있어, 인가 규칙을 따로
 * 걸지 않으면 아무나 남의 위시리스트에 장소를 담을 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("위시리스트 인가")
class PlaceWishlistAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("담기는 로그인해야 한다")
    void addRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/places/42/wishlists"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("빼기도 로그인해야 한다")
    void removeRequiresLogin() throws Exception {
        mockMvc.perform(delete("/api/v1/places/42/wishlists"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 목록도 로그인해야 한다")
    void listRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/wishlists"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("장소 상세는 그대로 열려 있다")
    void placeDetailStaysOpen() throws Exception {
        // 위시리스트 규칙이 /places/** 전체를 막아 버리면 장소 검색 화면이 통째로 죽는다.
        mockMvc.perform(get("/api/v1/places/999999999"))
                .andExpect(status().isNotFound());
    }
}
