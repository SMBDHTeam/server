package com.server.report.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.service.AccessTokenProvider;
import com.server.user.domain.AuthProvider;
import com.server.user.domain.User;
import com.server.user.domain.UserRole;
import com.server.user.repository.UserRepository;
import java.util.UUID;
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
 * 신고 요청 검증 실패 응답.
 *
 * <p>경로 분기가 없으면 신고 검증 실패가 "일정 조건이 올바르지 않습니다"로 나간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("신고 요청 검증")
class ReportRequestValidationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccessTokenProvider accessTokenProvider;
    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() {
        // 트랜잭션 없이 도는 테스트라 닉네임이 다른 테스트와 겹치지 않게 한다.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "report-" + suffix, suffix + "@example.com",
                "신고" + suffix, null, UserRole.USER));
        token = "Bearer " + accessTokenProvider.issue(user);
    }

    @Test
    @DisplayName("기타 사유에 설명이 없으면 INVALID_REPORT_REQUEST")
    void rejectsOtherWithoutDetail() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":1,\"reasonType\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_REQUEST"));
    }

    @Test
    @DisplayName("사유 유형이 없으면 INVALID_REPORT_REQUEST")
    void rejectsMissingReasonType() throws Exception {
        // 유형 도입 전 형식({ reason } 만)으로 보내는 클라이언트가 여기에 걸린다.
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":1,\"reason\":\"광고성 게시물입니다\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_REQUEST"));
    }
}
