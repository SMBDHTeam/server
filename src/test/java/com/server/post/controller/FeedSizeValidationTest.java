package com.server.post.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.service.AuthenticatedUser;
import com.server.user.domain.UserRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 문서가 "1 이상 50 이하"라고 적어 두고도 상한을 검증하지 않아, 큰 값을 보내면 조용히
 * 50으로 줄어 응답했다. 클라이언트는 요청한 만큼 못 받은 것인지 데이터가 없는 것인지
 * 구분할 수 없다. 문서와 동작이 어긋나지 않도록 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("피드 size 검증")
class FeedSizeValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("상한을 넘는 size 는 조용히 줄이지 않고 거절한다")
    void rejectsTooLargeSize() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("size", "1000").with(authentication(loggedIn())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POST_REQUEST"));
    }

    @Test
    @DisplayName("0 이하의 size 도 거절한다")
    void rejectsNonPositiveSize() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("size", "0").with(authentication(loggedIn())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상한값 자체는 허용한다")
    void acceptsUpperBound() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("size", "50").with(authentication(loggedIn())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("카테고리 자동완성은 상한이 30 이다")
    void hashtagSuggestionHasOwnBound() throws Exception {
        mockMvc.perform(get("/api/v1/categories/search").param("keyword", "부").param("size", "31"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/categories/search").param("keyword", "부").param("size", "30"))
                .andExpect(status().isOk());
    }

    /** 피드 목록은 로그인해야 열린다. size 검증까지 도달하려면 인증을 통과해야 한다. */
    private static Authentication loggedIn() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1L, UserRole.USER), null, List.of());
    }
}
