package com.server.report.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.service.AccessTokenProvider;
import com.server.post.domain.Post;
import com.server.post.repository.PostRepository;
import com.server.report.domain.ReportReasonType;
import com.server.report.domain.ReportTargetType;
import com.server.report.dto.ReportCreateRequest;
import com.server.report.service.ReportService;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
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
 * 내 신고 여부 조회.
 *
 * <p>신고 시트는 열 때 이 값을 본다. 없으면 이미 신고한 대상에서도 사유를 다시 고르게 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("내 신고 여부 조회")
class ReportCheckTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccessTokenProvider accessTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private ReportService reportService;

    private User reporter;
    private Post post;
    private String token;

    @BeforeEach
    void setUp() {
        reporter = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "check-reporter", "check-reporter@example.com", "신고확인자", null, UserRole.USER));
        User author = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "check-author", "check-author@example.com", "신고확인작성자", null, UserRole.USER));
        post = postRepository.saveAndFlush(new Post(author, "신고 여부 확인용 글"));
        token = "Bearer " + accessTokenProvider.issue(reporter);
    }

    @Test
    @DisplayName("로그인하지 않으면 401 이다")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/reports/me").param("targetType", "POST").param("targetId", String.valueOf(post.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("신고 전에는 false, 신고 후에는 true 다")
    void reflectsReport() throws Exception {
        mockMvc.perform(get("/api/v1/reports/me").header("Authorization", token)
                        .param("targetType", "POST").param("targetId", String.valueOf(post.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(false));

        reportService.report(reporter.getId(), new ReportCreateRequest(
                ReportTargetType.POST, post.getId(), ReportReasonType.SPAM, null));

        mockMvc.perform(get("/api/v1/reports/me").header("Authorization", token)
                        .param("targetType", "POST").param("targetId", String.valueOf(post.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));
    }

    @Test
    @DisplayName("대상 종류가 없으면 INVALID_REPORT_REQUEST 다")
    void rejectsMissingTargetType() throws Exception {
        mockMvc.perform(get("/api/v1/reports/me").header("Authorization", token)
                        .param("targetId", String.valueOf(post.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_REQUEST"));
    }
}
