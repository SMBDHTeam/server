package com.server.place.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.server.place.repository.PlaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@DisplayName("외부 장소 Resolve 통합")
class PlaceV2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaceRepository placeRepository;

    @Test
    @DisplayName("같은 Kakao 장소를 반복 확정해도 내부 장소 한 건만 유지한다")
    void resolveIsIdempotentBySourceAndExternalId() throws Exception {
        String body = """
                {
                  "source":"KAKAO_LOCAL",
                  "externalId":"kakao-resolve-1",
                  "name":"선택한 장소",
                  "category":"카페",
                  "address":"부산광역시 수영구",
                  "longitude":129.12,
                  "latitude":35.15,
                  "placeUrl":"https://place.map.kakao.com/1"
                }
                """;
        long before = placeRepository.count();
        MvcResult first = mockMvc.perform(post("/api/v1/places/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("KAKAO_LOCAL"))
                .andExpect(jsonPath("$.category").value("카페"))
                .andExpect(jsonPath("$.categoryLabel").value("카페"))
                .andExpect(jsonPath("$.address").value("부산광역시 수영구"))
                .andExpect(jsonPath("$.resolved").value(true))
                .andReturn();
        Number firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.placeId");

        mockMvc.perform(post("/api/v1/places/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(firstId.longValue()));

        assertThat(placeRepository.count()).isEqualTo(before + 1);
        assertThat(placeRepository.findBySourceAndExternalContentId("KAKAO_LOCAL", "kakao-resolve-1"))
                .get().extracting(place -> place.getPlaceUrl())
                .isEqualTo("https://place.map.kakao.com/1");
    }
}
