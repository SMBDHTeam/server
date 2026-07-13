package com.server.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.location.dto.LocationSearchResponse;
import com.server.location.service.LocationSearchService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Security 설정")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("API v1 요청은 인증 없이 접근할 수 있다")
    void apiV1AllowsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/locations/search").param("keyword", "부산역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("로컬 Planner 콘솔의 API preflight 요청을 허용한다")
    void plannerConsoleAllowsCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/schedules")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        LocationSearchService locationSearchService() {
            return new LocationSearchService(null) {
                @Override
                public LocationSearchResponse search(String keyword, int size) {
                    return new LocationSearchResponse(List.of());
                }
            };
        }
    }
}
