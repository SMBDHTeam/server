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
 * feed 파라미터를 문자열로 받던 동안에는 {@code "following"} 인지만 보고 나머지를 조용히
 * 전체 피드로 넘겼다. 오타를 내도 글이 나와 잘못 부른 줄 모르고, 실제로 인기 피드를 보려고
 * {@code ?feed=popular} 를 보냈다가 최신 피드를 인기 피드로 오해한 일이 있었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("피드 범위 파라미터")
class FeedScopeBindingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("소문자 following 을 받는다")
    void acceptsLowerCase() throws Exception {
        // 문자열로 받던 시절부터 클라이언트가 소문자로 보내 왔다. 그 계약을 유지한다.
        mockMvc.perform(get("/api/v1/posts").param("feed", "following")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("모르는 값은 조용히 넘기지 않고 거절한다")
    void rejectsUnknownValue() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("feed", "popular")
                        .with(authentication(loggedIn())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_POST_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("feed"));
    }

    @Test
    @DisplayName("생략하면 전체 피드다")
    void omittedMeansAll() throws Exception {
        mockMvc.perform(get("/api/v1/posts").with(authentication(loggedIn())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("빈 값은 보내지 않은 것과 같게 본다")
    void blankMeansAll() throws Exception {
        // 화면에서 값을 지운 채 요청이 나가는 경우가 있다. 이것까지 막으면 목록이 비어 버린다.
        mockMvc.perform(get("/api/v1/posts").param("feed", "")
                        .with(authentication(loggedIn())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인기 피드도 같은 규칙이다")
    void popularFeedSharesRule() throws Exception {
        mockMvc.perform(get("/api/v1/posts/popular").param("feed", "asdf")
                        .with(authentication(loggedIn())))
                .andExpect(status().isBadRequest());
    }

    private Authentication loggedIn() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1L, UserRole.USER),
                null,
                List.of());
    }
}
