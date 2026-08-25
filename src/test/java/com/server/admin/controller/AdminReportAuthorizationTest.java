package com.server.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.service.AccessTokenProvider;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 신고 API 의 인가.
 *
 * <p>사용자 API 는 아직 공개라 이 경로만 닫혀 있다. 여기가 열려 있으면 누구나 신고를
 * 처리하고 남의 게시물을 지울 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("관리자 신고 API 인가")
class AdminReportAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    private String token(UserRole role) {
        User user = User.ofOAuth(AuthProvider.GOOGLE, "sub", "a@example.com", "동준", null, role);
        ReflectionTestUtils.setField(user, "id", 1L);
        return "Bearer " + accessTokenProvider.issue(user);
    }

    @Test
    @DisplayName("토큰 없이 신고 목록을 볼 수 없다")
    void rejectsAnonymousReportList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("일반 사용자는 신고 목록을 볼 수 없다")
    void rejectsNonAdminReportList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports").header("Authorization", token(UserRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("일반 사용자는 관리자 삭제를 쓸 수 없다")
    void rejectsNonAdminDeletion() throws Exception {
        // 이게 뚫리면 누구나 남의 게시물과 댓글을 지울 수 있다.
        mockMvc.perform(delete("/api/v1/admin/posts/1").header("Authorization", token(UserRole.USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/comments/1")
                        .header("Authorization", token(UserRole.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("일반 사용자는 신고 상태를 바꿀 수 없다")
    void rejectsNonAdminStatusChange() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/reports/1")
                        .header("Authorization", token(UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("일반 사용자는 사용자·장소·통계 관리자 API 를 쓸 수 없다")
    void rejectsNonAdminAcrossAdminApis() throws Exception {
        // 관리자 경로가 늘어날 때 한 곳이라도 빠지면 그 API 만 열린다.
        for (String path : new String[]{
                "/api/v1/admin/users",
                "/api/v1/admin/places/ingestion",
                "/api/v1/admin/places/hidden",
                "/api/v1/admin/stats/summary",
                "/api/v1/admin/stats/popular?type=PLACE"}) {
            mockMvc.perform(get(path).header("Authorization", token(UserRole.USER)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("관리자는 통계와 장소 관리를 볼 수 있다")
    void allowsAdminAcrossAdminApis() throws Exception {
        for (String path : new String[]{
                "/api/v1/admin/users",
                "/api/v1/admin/places/hidden",
                "/api/v1/admin/stats/summary"}) {
            mockMvc.perform(get(path).header("Authorization", token(UserRole.ADMIN)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("관리자는 신고 목록을 볼 수 있다")
    void allowsAdminReportList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports").header("Authorization", token(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalCount").exists());
    }
}
