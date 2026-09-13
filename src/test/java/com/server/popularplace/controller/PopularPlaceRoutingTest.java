package com.server.popularplace.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code /places/popular} 은 장소 상세의 {@code /places/{placeId}} 와 같은 자리에 온다.
 * 글자가 그대로 맞는 쪽이 이겨야 하며, 그러지 않으면 "popular" 를 장소 ID 로 읽어 400 이 난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("인기 장소 경로")
class PopularPlaceRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("장소 상세 경로에 가리지 않는다")
    void popularWinsOverPlaceDetail() throws Exception {
        mockMvc.perform(get("/api/v1/places/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("로그인 없이 볼 수 있다")
    void allowsAnonymous() throws Exception {
        // 홈 화면 첫 화면에 쓰는 목록이다. 장소 검색·상세와 같은 수준으로 열어 둔다.
        mockMvc.perform(get("/api/v1/places/popular").param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("size 상한을 넘기면 거절한다")
    void rejectsTooLargeSize() throws Exception {
        mockMvc.perform(get("/api/v1/places/popular").param("size", "1000"))
                .andExpect(status().isBadRequest());
    }
}
