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
    @DisplayName("OpenAPI 문서에 일정 생성 더미 요청 두 종류를 제공한다")
    void apiDocsContainScheduleCreateExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Tour Server API"))
                .andExpect(jsonPath("$.paths['/api/v1/schedules'].post.requestBody.content['application/json'].examples.oneDay").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules'].post.requestBody.content['application/json'].examples.fourDay.value.mustVisitPlaceIds.length()").value(4));
    }

    @Test
    @DisplayName("현재 로컬 장소 ID가 연결된 3박 4일 예제를 그대로 실행할 수 있다")
    void fourDayExampleCreatesSchedule() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode example = objectMapper.readTree(apiDocs)
                .at("/paths/~1api~1v1~1schedules/post/requestBody/content/application~1json/examples/fourDay/value");

        mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(example)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days.length()").value(4))
                .andExpect(jsonPath("$.evaluation.hardGate.passed").value(true));
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
