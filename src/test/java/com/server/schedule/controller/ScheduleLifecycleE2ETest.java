package com.server.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@DisplayName("일정 생명주기 E2E")
class ScheduleLifecycleE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private List<Place> places;

    @BeforeEach
    void setUp() {
        places = placeRepository.saveAllAndFlush(List.of(
                place("E2E-1", "광안리해수욕장", "129.1186", "35.1532"),
                place("E2E-2", "송도해수욕장", "129.0172", "35.0770"),
                place("E2E-3", "국제시장", "129.0286", "35.1025"),
                place("E2E-4", "부산박물관", "129.0840", "35.1296"),
                place("E2E-5", "자갈치시장", "129.0305", "35.0967")
        ));
    }

    @Test
    @DisplayName("생성부터 수정·공유·지도·폐기까지 전체 흐름을 완료한다")
    void completeLifecycle() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(places.get(0).getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluation.hardGate.passed").value(true))
                .andReturn();
        String created = createResult.getResponse().getContentAsString();
        ScheduleE2EAssertions.assertSuccessfulCreate(
                created,
                LocalDate.parse("2026-08-10"),
                1,
                List.of(places.get(0).getId())
        );
        String scheduleId = JsonPath.read(created, "$.id");
        List<String> stopIds = JsonPath.read(created, "$.days[0].stops[*].id");
        List<Integer> scheduledPlaceIds = JsonPath.read(created, "$.days[0].stops[*].place.id");
        long addedPlaceId = places.stream()
                .map(Place::getId)
                .filter(id -> !scheduledPlaceIds.contains(id.intValue()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(scheduleId))
                .andExpect(jsonPath("$.items[0].evaluation").doesNotExist());

        String patchRequest = """
                {
                  "stops": [
                    {"stopId":"%s","dayNo":1,"order":1,"stayMinutes":70},
                    {"placeId":%d,"dayNo":1,"order":2,"stayMinutes":60}
                  ]
                }
                """.formatted(stopIds.get(0), addedPlaceId);
        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].stops.length()").value(2))
                .andExpect(jsonPath("$.days[0].stops[0].id").value(stopIds.get(0)))
                .andExpect(jsonPath("$.days[0].finalTransit").exists())
                .andExpect(jsonPath("$.evaluation").doesNotExist());

        MvcResult shareResult = mockMvc.perform(post("/api/v1/schedules/{scheduleId}/shares", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInDays\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        String share = shareResult.getResponse().getContentAsString();
        String shareId = JsonPath.read(share, "$.id");
        String token = JsonPath.read(share, "$.token");
        entityManager.flush();
        String storedHash = jdbcTemplate.queryForObject(
                "select token_hash from share_links where id = ?",
                String.class,
                java.util.UUID.fromString(shareId)
        );
        assertThat(storedHash).hasSize(64).isNotEqualTo(token);

        mockMvc.perform(get("/api/v1/shared-schedules/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scheduleId))
                .andExpect(jsonPath("$.readOnly").value(true));
        mockMvc.perform(get("/api/v1/shared-schedules/{token}/map", token).param("dayNo", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markers.length()").value(2))
                .andExpect(jsonPath("$.routeLines").isArray());

        mockMvc.perform(delete(
                        "/api/v1/schedules/{scheduleId}/shares/{shareId}", scheduleId, shareId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/shared-schedules/{token}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_LINK_NOT_FOUND"));
    }

    @Test
    @DisplayName("2박 3일의 일차별 출발지와 도착지를 독립적으로 적용한다")
    void createsMultiDayScheduleWithDifferentDailyEndpoints() throws Exception {
        List<Long> mustVisitPlaceIds = List.of(
                places.get(0).getId(),
                places.get(2).getId(),
                places.get(4).getId()
        );

        String created = mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(multiDayCreateRequest(mustVisitPlaceIds)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].startLocation.name").value("부산역"))
                .andExpect(jsonPath("$.days[0].endLocation.name").value("광안리 숙소"))
                .andExpect(jsonPath("$.days[1].startLocation.name").value("광안리 숙소"))
                .andExpect(jsonPath("$.days[1].endLocation.name").value("남포동 숙소"))
                .andExpect(jsonPath("$.days[2].startLocation.name").value("남포동 숙소"))
                .andExpect(jsonPath("$.days[2].endLocation.name").value("김해국제공항"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        ScheduleE2EAssertions.assertSuccessfulCreate(
                created,
                LocalDate.parse("2026-08-10"),
                3,
                mustVisitPlaceIds
        );
    }

    @Test
    @DisplayName("잘못된 수정 요청은 400을 반환하고 기존 일정을 보존한다")
    void invalidUpdateRollsBack() throws Exception {
        String created = mockMvc.perform(post("/api/v1/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(places.get(0).getId())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String scheduleId = JsonPath.read(created, "$.id");
        List<String> originalStopIds = JsonPath.read(created, "$.days[0].stops[*].id");

        mockMvc.perform(patch("/api/v1/schedules/{scheduleId}", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stops": [
                                    {"placeId":1,"dayNo":1,"order":1,"stayMinutes":60},
                                    {"placeId":2,"dayNo":1,"order":2,"stayMinutes":60},
                                    {"placeId":3,"dayNo":1,"order":3,"stayMinutes":60},
                                    {"placeId":4,"dayNo":1,"order":4,"stayMinutes":60}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_CONDITION"));

        String listed = mockMvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> persistedStopIds = JsonPath.read(listed, "$.items[0].days[0].stops[*].id");
        assertThat(persistedStopIds).containsExactlyElementsOf(originalStopIds);
    }

    private String createRequest(long mustVisitPlaceId) {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-10",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"19:00",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "endLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "selectedAnswers":[
                    {"questionId":"COMPANION","answerId":"COMPANION_PARENTS"},
                    {"questionId":"PACE","answerId":"PACE_BALANCED"},
                    {"questionId":"THEME","answerId":"THEME_LOCAL"},
                    {"questionId":"MOBILITY","answerId":"MOBILITY_LOW_WALK"},
                    {"questionId":"TRANSIT","answerId":"TRANSIT_SIMPLE"}
                  ],
                  "mustVisitPlaceIds":[%d]
                }
                """.formatted(mustVisitPlaceId);
    }

    private String multiDayCreateRequest(List<Long> mustVisitPlaceIds) {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-12",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"19:00",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "endLocation":{"name":"김해국제공항","longitude":128.9485,"latitude":35.1732},
                  "selectedAnswers":[
                    {"questionId":"COMPANION","answerId":"COMPANION_PARENTS"},
                    {"questionId":"PACE","answerId":"PACE_BALANCED"},
                    {"questionId":"THEME","answerId":"THEME_LOCAL"},
                    {"questionId":"MOBILITY","answerId":"MOBILITY_LOW_WALK"},
                    {"questionId":"TRANSIT","answerId":"TRANSIT_SIMPLE"}
                  ],
                  "mustVisitPlaceIds":[%d,%d,%d],
                  "days":[
                    {
                      "dayNo":1,
                      "startTime":"09:00",
                      "endTime":"19:00",
                      "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                      "endLocation":{"name":"광안리 숙소","longitude":129.1186,"latitude":35.1532}
                    },
                    {
                      "dayNo":2,
                      "startTime":"09:00",
                      "endTime":"19:00",
                      "startLocation":{"name":"광안리 숙소","longitude":129.1186,"latitude":35.1532},
                      "endLocation":{"name":"남포동 숙소","longitude":129.0320,"latitude":35.1000}
                    },
                    {
                      "dayNo":3,
                      "startTime":"09:00",
                      "endTime":"17:00",
                      "startLocation":{"name":"남포동 숙소","longitude":129.0320,"latitude":35.1000},
                      "endLocation":{"name":"김해국제공항","longitude":128.9485,"latitude":35.1732}
                    }
                  ]
                }
                """.formatted(
                mustVisitPlaceIds.get(0),
                mustVisitPlaceIds.get(1),
                mustVisitPlaceIds.get(2)
        );
    }

    private Place place(String externalId, String name, String longitude, String latitude) {
        return new Place(
                "TOUR_API", externalId, "12", name, "관광지", "부산",
                new BigDecimal(longitude), new BigDecimal(latitude), null
        );
    }
}
