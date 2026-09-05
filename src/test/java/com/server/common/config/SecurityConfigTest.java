package com.server.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @DisplayName("즉흥여행 출발지 검색은 인증 없이 접근하고 입력 검증을 적용한다")
    void spontaneousStartLocationSearchAllowsAnonymousRequests() throws Exception {
        String path = "/api/v1/spontaneous-trips/start-locations/search";
        mockMvc.perform(get(path).param("keyword", "부산역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get(path).param("keyword", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPONTANEOUS_TRIP_REQUEST"));
        mockMvc.perform(get(path).param("keyword", "부산역").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPONTANEOUS_TRIP_REQUEST"));
    }

    @Test
    @DisplayName("공유 링크로 들어온 게시물과 그 댓글은 로그인 없이 볼 수 있다")
    void sharedPostIsPublic() throws Exception {
        // 여기까지 막으면 공유 링크가 로그인 화면으로만 이어진다. 없는 게시물이라
        // 404 지만 인가는 통과했다는 뜻이다.
        mockMvc.perform(get("/api/v1/posts/1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/posts/1/comments")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("피드 목록과 프로필은 로그인해야 볼 수 있다")
    void feedAndProfileRequireLogin() throws Exception {
        // 앱은 로그인해야 들어오는 구조다. 둘러보기는 로그인한 사람의 몫이고,
        // 비로그인에게 여는 것은 공유된 글 하나뿐이다.
        mockMvc.perform(get("/api/v1/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/posts/popular")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/1/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/search").param("keyword", "가")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("커뮤니티 쓰기는 로그인해야 한다")
    void communityWriteRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/posts/1/likes")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/posts/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/users/1/follows")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 것을 읽는 경로는 조회여도 로그인해야 한다")
    void ownDataReadRequiresLogin() throws Exception {
        // 남의 것을 읽는 조회와 달리 요청자가 누구인지 알아야 응답을 만들 수 있다.
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/bookmarks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/posts/me/deleted")).andExpect(status().isUnauthorized());
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
    @DisplayName("걷어낸 X-User-Id 헤더는 preflight에서 더 이상 허용하지 않는다")
    void doesNotAllowLegacyUserIdHeaderInPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/posts")
                        .header("Origin", "https://www.busantour.site")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, X-User-Id"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("X-User-Id"))));
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
