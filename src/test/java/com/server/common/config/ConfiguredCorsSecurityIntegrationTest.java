package com.server.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "CORS_ALLOWED_ORIGINS=https://frontend.example")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("환경별 CORS 설정")
class ConfiguredCorsSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("환경변수 Origin을 preflight에 적용한다")
    void allowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/schedules")
                        .header("Origin", "https://frontend.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example"));
    }

    @Test
    @DisplayName("환경변수에 없는 Origin의 preflight를 거부한다")
    void rejectsUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/schedules")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
