package com.server.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.admin.service.AdminUserService;
import com.server.auth.service.AccessTokenProvider;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 정지된 계정의 쓰기 차단.
 *
 * <p>정지 상태를 저장만 하고 확인하는 곳이 없으면 정지는 아무것도 막지 못한다.
 * 관리자가 정지시켜도 그 사용자는 계속 글을 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("정지 계정 쓰기 차단")
class SuspendedUserWriteFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccessTokenProvider accessTokenProvider;
    @Autowired
    private AdminUserService adminUserService;
    private User user;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        user = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "suspend-sub", "s@example.com", "정지대상", null, UserRole.USER));
        token = "Bearer " + accessTokenProvider.issue(user);
    }

    private void suspend() {
        adminUserService.updateStatus(user.getId(), true, 7, "광고성 게시물 반복");
    }

    @Test
    @DisplayName("정지되면 쓰기 요청이 403 이다")
    void blocksWriteWhenSuspended() throws Exception {
        suspend();

        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"글\",\"mediaList\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"));
    }

    @Test
    @DisplayName("정지돼도 읽기는 열려 있다")
    void allowsReadWhenSuspended() throws Exception {
        // 읽기까지 막으면 정지된 사용자가 자기 상태를 확인할 방법이 없다.
        suspend();

        mockMvc.perform(get("/api/v1/posts").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("정지돼도 로그인 갱신 경로는 막지 않는다")
    void allowsAuthPathWhenSuspended() throws Exception {
        suspend();

        // 토큰 형식이 틀려 거절되더라도 USER_SUSPENDED 로 막히지는 않아야 한다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"garbage\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("정지 해제하면 쓰기가 다시 열린다")
    void unblocksAfterRelease() throws Exception {
        suspend();
        adminUserService.updateStatus(user.getId(), false, null, null);

        // 본문 검증에서 걸리더라도 USER_SUSPENDED 는 아니어야 한다.
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\",\"mediaList\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 없이 X-User-Id 만 보내도 정지된 사용자는 막힌다")
    void blocksLegacyHeaderWriteWhenSuspended() throws Exception {
        // 커뮤니티 쓰기는 아직 X-User-Id 로 작성자를 정한다. 토큰만 보면 정지된 사용자가
        // Authorization 을 빼고 이 헤더만 보내 그대로 글을 쓸 수 있다.
        suspend();

        mockMvc.perform(post("/api/v1/posts")
                        .header("X-User-Id", String.valueOf(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"우회 시도\",\"mediaList\":"
                                + "[{\"url\":\"http://x/a.jpg\",\"mediaType\":\"IMAGE\",\"sortOrder\":0}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"));
    }

    @Test
    @DisplayName("정지되지 않은 사용자의 쓰기는 막지 않는다")
    void doesNotBlockActiveUser() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\",\"mediaList\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
