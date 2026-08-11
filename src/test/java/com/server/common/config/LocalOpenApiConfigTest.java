package com.server.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("로컬 OpenAPI 설정")
class LocalOpenApiConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlaceRepository placeRepository;

    @BeforeEach
    void seedPlaces() {
        if (placeRepository.count() > 0) {
            return;
        }
        placeRepository.saveAll(List.of(
                place("SWAGGER_1", "감천문화마을", "129.0106", "35.0974"),
                place("SWAGGER_2", "송도해수욕장", "129.0172", "35.0770"),
                place("SWAGGER_3", "자갈치시장", "129.0305", "35.0967"),
                place("SWAGGER_4", "광안리해수욕장", "129.1186", "35.1532")
        ));
    }

    @Test
    @DisplayName("일정 생성은 프론트가 쓰는 Preview 기반 요청으로 문서화한다")
    void apiDocsDocumentPreviewBasedCreate() throws Exception {
        // 같은 경로에 V1(Idempotency-Key 없음)과 V2 두 핸들러가 있지만 OpenAPI 는
        // 경로+메서드당 하나만 표현할 수 있다. 프론트가 실제로 쓰는 V2 를 노출한다.
        String post = "$.paths['/api/v1/schedules'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Tour Server API"))
                .andExpect(jsonPath(post + ".requestBody.content['application/json'].schema.$ref")
                        .value("#/components/schemas/SchedulePreviewScheduleRequest"))
                .andExpect(jsonPath(post + ".requestBody.content['application/json'].examples.fromPreview.value.previewId")
                        .exists())
                .andExpect(jsonPath(post + ".parameters[?(@.name == 'Idempotency-Key')].required")
                        .value(org.hamcrest.Matchers.hasItem(true)));
    }

    @Test
    @DisplayName("프론트 일정 생성 경로의 모든 엔드포인트에 예시가 있다")
    void apiDocsCoverFrontendFlow() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 본문을 받는 단계는 요청 예시를 제공한다
                .andExpect(jsonPath("$.paths['/api/v1/places/resolve'].post.requestBody.content['application/json'].examples.naver").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedule-previews'].post.requestBody.content['application/json'].examples.frontendBasic").exists())
                // 경로 변수와 주요 쿼리는 example 을 제공한다
                .andExpect(jsonPath("$.paths['/api/v1/schedule-previews/{previewId}'].get.parameters[0].example").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}'].get.parameters[0].example").exists())
                .andExpect(jsonPath("$.paths['/api/v1/locations/search'].get.parameters[0].example").exists());
    }

    @Test
    @DisplayName("Swagger UI 정적 페이지를 제공한다")
    void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private Place place(String externalContentId, String name, String longitude, String latitude) {
        return new Place(
                "LOCAL_FIXTURE",
                externalContentId,
                "12",
                name,
                "관광지",
                "부산광역시",
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                null
        );
    }
}
