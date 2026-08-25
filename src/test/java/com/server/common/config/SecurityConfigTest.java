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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    @Test
    @DisplayName("로컬 프론트의 V2 생성 preflight 요청을 허용한다")
    void localFrontendAllowsScheduleV2CorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/schedules")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Idempotency-Key")
                ));
    }

    @Test
    @DisplayName("배포 프론트의 V2 생성 preflight 요청을 허용한다")
    void deployedFrontendAllowsScheduleV2CorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/schedules")
                        .header("Origin", "https://www.busantour.site")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.busantour.site"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Idempotency-Key")
                ));
    }

    @Test
    @DisplayName("로그인 요청의 Authorization 헤더를 preflight에서 허용한다")
    void allowsAuthorizationHeaderInPreflight() throws Exception {
        // 허용 헤더에 Authorization 이 없으면 브라우저에서 로그인한 요청이 전부 막힌다.
        mockMvc.perform(options("/api/v1/schedules")
                        .header("Origin", "https://www.busantour.site")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")));
    }

    @Test
    @DisplayName("커뮤니티가 쓰는 X-User-Id 헤더를 preflight에서 허용한다")
    void allowsUserIdHeaderInPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/posts")
                        .header("Origin", "https://www.busantour.site")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, X-User-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("X-User-Id")));
    }

    @Test
    @DisplayName("브라우저가 응답에서 X-Trace-Id를 읽을 수 있도록 노출한다")
    void exposesTraceIdHeader() throws Exception {
        // TraceIdFilter 가 모든 응답에 넣고 오류 응답의 traceId 와 같은 값이라고 문서화하지만,
        // 노출 헤더로 지정하지 않으면 다른 오리진의 스크립트는 읽을 수 없다.
        mockMvc.perform(get("/api/v1/locations/search")
                        .param("keyword", "부산역")
                        .header("Origin", "https://www.busantour.site"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Expose-Headers",
                        org.hamcrest.Matchers.containsString("X-Trace-Id")));
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
