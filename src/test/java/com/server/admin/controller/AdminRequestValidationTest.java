package com.server.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * 관리자 요청 검증 실패 응답.
 *
 * <p>경로 분기가 없으면 관리자 검증 실패가 "일정 조건이 올바르지 않습니다"로 나간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("관리자 요청 검증")
class AdminRequestValidationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccessTokenProvider accessTokenProvider;
    @Autowired
    private UserRepository userRepository;

    private User admin;
    private String token;

    @BeforeEach
    void setUp() {
        // 트랜잭션 없이 도는 테스트라 닉네임이 다른 테스트와 겹치지 않게 한다.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = userRepository.saveAndFlush(User.ofOAuth(
                AuthProvider.GOOGLE, "admin-valid-" + suffix, suffix + "@example.com",
                "검증관리자" + suffix, null, UserRole.ADMIN));
        token = "Bearer " + accessTokenProvider.issue(admin);
    }

    @Test
    @DisplayName("정지 사유가 없으면 INVALID_ADMIN_REQUEST")
    void rejectsSuspensionWithoutReason() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + admin.getId() + "/status")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suspended\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_REQUEST"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 없으면 INVALID_ADMIN_REQUEST")
    void rejectsMissingRequiredParameter() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats/trend").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_REQUEST"));
    }

    @Test
    @DisplayName("열거형에 없는 쿼리 값이면 INVALID_ADMIN_REQUEST")
    void rejectsUnknownEnumParameter() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports").param("status", "DONE").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_REQUEST"));
    }
}
