package com.server.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.config.AuthProperties;
import com.server.auth.service.AccessTokenProvider;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 경로 인가와 인증 오류 응답 형태.
 *
 * <p>관리자 컨트롤러가 아직 없으므로 테스트 전용 컨트롤러로 경로만 만들어 확인한다.
 * 실제 관리자 기능을 붙이기 전에 인가가 걸려 있어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityAuthorizationTest.AdminPingController.class)
@ActiveProfiles("test")
@DisplayName("인가와 인증 오류 응답")
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    private String tokenFor(UserRole role) {
        User user = User.ofOAuth(AuthProvider.GOOGLE, "sub", "a@example.com", "동준", null, role);
        ReflectionTestUtils.setField(user, "id", 1L);
        return accessTokenProvider.issue(user);
    }

    @Test
    @DisplayName("토큰 없이 관리자 경로에 접근하면 401 이다")
    void rejectsAnonymousAdminAccess() throws Exception {
        // SecurityConfig 가 /api/v1/** 를 통째로 permitAll 하던 시절에는 200 이 나갔다.
        // 관리자 기능을 붙이기 전에 이 경로가 막혀 있어야 한다.
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자 토큰으로 관리자 경로에 접근하면 403 이다")
    void rejectsNonAdminAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 토큰이면 통과한다")
    void allowsAdminAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("401 응답도 code·fieldErrors·traceId 를 갖춘다")
    void unauthorizedUsesCommonErrorShape() throws Exception {
        // Security 예외는 필터에서 나 @ControllerAdvice 가 잡지 못한다. 그대로 두면
        // 401·403 만 Spring 기본 응답으로 나가 클라이언트의 code 분기가 깨진다.
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("403 응답도 같은 형태이며 traceId 가 응답 헤더와 일치한다")
    void forbiddenUsesCommonErrorShape() throws Exception {
        var result = mockMvc.perform(get("/api/v1/admin/ping")
                        .header("Authorization", "Bearer " + tokenFor(UserRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();

        String header = result.getResponse().getHeader("X-Trace-Id");
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .as("본문의 traceId 가 헤더와 달라지면 로그와 이어 볼 수 없다")
                .contains("\"traceId\":\"" + header + "\"");
    }

    @Test
    @DisplayName("잘못된 토큰을 보내도 공개 경로는 열려 있다")
    void keepsPublicPathsOpenWithBadToken() throws Exception {
        // 인증 필터는 토큰을 요구하지 않는다. 잘못된 토큰에 401 을 내면 비로그인도 볼 수
        // 있어야 하는 화면이 깨진다.
        mockMvc.perform(get("/api/v1/trip-questions")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isOk());
    }

    @RestController
    static class AdminPingController {

        @GetMapping("/api/v1/admin/ping")
        String ping() {
            return "pong";
        }
    }
}
