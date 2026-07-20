package com.server.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.repository.ScheduleRepository;
import java.math.BigDecimal;
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
@DisplayName("일정 생성 V2 E2E")
class ScheduleV2E2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Place> places;

    @BeforeEach
    void setUp() {
        places = placeRepository.saveAllAndFlush(List.of(
                place("V2-1", "광안리해수욕장", "129.1186", "35.1532"),
                place("V2-2", "송도해수욕장", "129.0172", "35.0770"),
                place("V2-3", "국제시장", "129.0286", "35.1025"),
                place("V2-4", "부산박물관", "129.0840", "35.1296"),
                place("V2-5", "자갈치시장", "129.0305", "35.0967"),
                place("V2-6", "해운대해수욕장", "129.1604", "35.1587"),
                place("V2-7", "부산시민공원", "129.0595", "35.1667"),
                place("V2-8", "흰여울문화마을", "129.0443", "35.0787"),
                place("V2-9", "용두산공원", "129.0324", "35.1007")
        ));
    }

    @Test
    @DisplayName("숙소 미정 2박 3일을 Preview로 확인하고 멱등하게 한 건만 생성한다")
    void createsUndecidedMultiDayScheduleIdempotently() throws Exception {
        MvcResult previewResult = mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(undecidedPreviewRequest(places.get(0).getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.canGenerate").value(true))
                .andExpect(jsonPath("$.lodgingMode").value("UNDECIDED"))
                .andExpect(jsonPath("$.routeCoverage").value("ATTRACTION_ROUTES_ONLY"))
                .andExpect(jsonPath("$.resolvedDays.length()").value(3))
                .andExpect(jsonPath("$.resolvedDays[0].startLocation.name").value("부산역"))
                .andExpect(jsonPath("$.resolvedDays[1].startLocation").doesNotExist())
                .andExpect(jsonPath("$.resolvedDays[1].endLocation").doesNotExist())
                .andExpect(jsonPath("$.resolvedDays[2].endLocation.name").value("김해국제공항"))
                .andExpect(jsonPath("$.resolvedEndConstraint.appliedBufferMinutes").value(90))
                .andExpect(jsonPath("$.resolvedEndConstraint.targetAt")
                        .value("2026-08-12T20:00:00+09:00"))
                .andExpect(jsonPath("$.appliedDefaults[1].resolvedValue").value("10:00:00"))
                .andExpect(jsonPath("$.interpretedPrompt.source").value("RULE_BASED"))
                .andExpect(jsonPath("$.interpretedPrompt.confidence").value(100))
                .andExpect(jsonPath("$.warnings[0].code").value("LODGING_ROUTE_EXCLUDED"))
                .andReturn();

        String previewId = JsonPath.read(
                previewResult.getResponse().getContentAsString(), "$.previewId");
        String creationBody = "{\"previewId\":\"" + previewId + "\"}";
        String key = "d75da78c-2d68-4c3e-83c7-3f07f2cd3e0e";
        long before = scheduleRepository.count();

        MvcResult first = mockMvc.perform(post("/api/v1/schedules")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creationBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previewId").value(previewId))
                .andExpect(jsonPath("$.planningAssumptions.lodgingMode").value("UNDECIDED"))
                .andExpect(jsonPath("$.planningAssumptions.routeCoverage")
                        .value("ATTRACTION_ROUTES_ONLY"))
                .andExpect(jsonPath("$.evaluation.qualityScore.evaluationCoveragePercent").value(90))
                .andExpect(jsonPath("$.evaluation.qualityScore.unusedMinutes").isNumber())
                .andExpect(jsonPath("$.evaluation.qualityScore.longTransitWarnings").isArray())
                .andExpect(jsonPath("$.evaluation.qualityScore.routeConfidence").value("LOW"))
                .andExpect(jsonPath("$.evaluation.qualityScore.metrics[4].status").value("NOT_EVALUATED"))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[1].startLocation.name").isNotEmpty())
                .andExpect(jsonPath("$.days[1].startLocationSource").value("PLANNER_DECIDES"))
                .andExpect(jsonPath("$.days[1].endLocation.name").isNotEmpty())
                .andExpect(jsonPath("$.days[1].endLocationSource").value("LAST_STOP"))
                .andExpect(jsonPath("$.days[1].summary").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("null"))))
                .andExpect(jsonPath("$.days[1].summary").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("도착"))))
                .andReturn();
        String scheduleId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creationBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(scheduleId))
                .andExpect(jsonPath("$.evaluation.qualityScore.evaluationCoveragePercent").value(90));

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Idempotency-Key", "c2f29a83-6509-4e31-b35a-8a47f56c1272")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creationBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_ALREADY_CONSUMED"))
                .andExpect(jsonPath("$.scheduleId").value(scheduleId));

        assertThat(scheduleRepository.count()).isEqualTo(before + 1);
        mockMvc.perform(get("/api/v1/schedule-previews/{previewId}", previewId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSUMED"))
                .andExpect(jsonPath("$.scheduleId").value(scheduleId));
        mockMvc.perform(get("/api/v1/schedules/{scheduleId}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scheduleId));
        mockMvc.perform(get("/api/v1/schedules/{scheduleId}/map", scheduleId).param("dayNo", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startMarker.name").isNotEmpty())
                .andExpect(jsonPath("$.endMarker.name").isNotEmpty())
                .andExpect(jsonPath("$.routeLines[0].distanceMeters")
                        .value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("겹치는 고정 행사는 수정 가능한 Preview 충돌로 반환한다")
    void returnsFixedEventConflict() throws Exception {
        long firstPlaceId = places.get(0).getId();
        long secondPlaceId = places.get(1).getId();
        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedEventConflictRequest(firstPlaceId, secondPlaceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUIRES_ACTION"))
                .andExpect(jsonPath("$.canGenerate").value(false))
                .andExpect(jsonPath("$.conflicts[0].code").value("FIXED_EVENT_CONFLICT"));
    }

    @Test
    @DisplayName("고정 행사는 지정 날짜와 시각의 stop으로 저장한다")
    void persistsFixedEventAtExactTime() throws Exception {
        long eventPlaceId = places.get(0).getId();
        MvcResult preview = mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedEventRequest(eventPlaceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andReturn();
        String previewId = JsonPath.read(preview.getResponse().getContentAsString(), "$.previewId");

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Idempotency-Key", "221f975e-9135-44b9-b771-9503a1b9cf91")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"" + previewId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].stops[2].place.id").value(eventPlaceId))
                .andExpect(jsonPath("$.days[0].stops[2].arriveAt").value("18:00:00"))
                .andExpect(jsonPath("$.days[0].stops[2].departAt").value("19:00:00"));

        Integer eventCount = jdbcTemplate.queryForObject(
                "select count(*) from schedule_fixed_events where client_event_id = 'event-evening'",
                Integer.class);
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("고정 숙소는 첫날 이후 일차의 출발과 모든 일차의 도착에 적용한다")
    void resolvesFixedBaseLodging() throws Exception {
        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedBasePreviewRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.routeCoverage").value("FULL"))
                .andExpect(jsonPath("$.resolvedDays[0].startLocation.name").value("부산역"))
                .andExpect(jsonPath("$.resolvedDays[0].endLocation.name").value("해운대 숙소"))
                .andExpect(jsonPath("$.resolvedDays[1].startLocation.name").value("해운대 숙소"))
                .andExpect(jsonPath("$.resolvedDays[1].endLocation.name").value("해운대 숙소"));
    }

    @Test
    @DisplayName("날짜별 숙소는 전날 도착지와 다음 날 출발지로 연결한다")
    void resolvesPerNightLodging() throws Exception {
        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(perNightPreviewRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.resolvedDays[0].endLocation.name").value("해운대 숙소"))
                .andExpect(jsonPath("$.resolvedDays[1].startLocation.name").value("해운대 숙소"))
                .andExpect(jsonPath("$.resolvedDays[1].endLocation.name").value("남포동 숙소"))
                .andExpect(jsonPath("$.resolvedDays[2].startLocation.name").value("남포동 숙소"))
                .andExpect(jsonPath("$.resolvedDays[2].endLocation").doesNotExist());
    }

    @Test
    @DisplayName("숙소 계획을 생략하면 숙소 미정 모드로 Preview를 생성한다")
    void defaultsMissingLodgingPlanToUndecided() throws Exception {
        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPreviewRequest(selectedAnswers())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.lodgingMode").value("UNDECIDED"))
                .andExpect(jsonPath("$.routeCoverage").value("ATTRACTION_ROUTES_ONLY"));
    }

    @Test
    @DisplayName("활성 필수 선호도 질문이 누락되면 Preview를 생성하지 않는다")
    void rejectsMissingRequiredPreferenceAnswer() throws Exception {
        String answersWithoutTransit = """
                [
                  {"questionId":"COMPANION","answerIds":["COMPANION_FRIENDS"]},
                  {"questionId":"PACE","answerIds":["PACE_RELAXED"]},
                  {"questionId":"THEME","answerIds":["THEME_NATURE"]},
                  {"questionId":"MOBILITY","answerIds":["MOBILITY_NORMAL"]}
                ]
                """;

        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalPreviewRequest(answersWithoutTransit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_PREVIEW_REQUEST"));
    }

    @Test
    @DisplayName("숙소 계획이 누락되면 Preview 입력 오류로 반환한다")
    void rejectsMissingLodgingPlanWithPreviewErrorCode() throws Exception {
        String request = """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-10",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "selectedAnswers":%s
                }
                """.formatted(selectedAnswers());

        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_PREVIEW_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lodgingPlan"));
    }

    @Test
    @DisplayName("날짜별 숙소에서 숙박일 하나가 빠지면 Preview를 생성하지 않는다")
    void rejectsMissingPerNightLodging() throws Exception {
        String request = """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-12",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "lodgingPlan":{
                    "mode":"PER_NIGHT",
                    "nightStays":[
                      {"date":"2026-08-10","location":{"name":"해운대 숙소","longitude":129.158,"latitude":35.159}}
                    ]
                  },
                  "selectedAnswers":%s
                }
                """.formatted(selectedAnswers());

        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PER_NIGHT_LOCATION_MISSING"));
    }

    @Test
    @DisplayName("자유 요청 길이 제한 위반은 Preview 입력 오류 코드로 반환한다")
    void rejectsOversizedCustomPromptWithPreviewErrorCode() throws Exception {
        String request = minimalPreviewRequest(selectedAnswers()).replace(
                "\n}", ",\n  \"customPrompt\":\"" + "가".repeat(501) + "\"\n}");

        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_PREVIEW_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("customPrompt"));
    }

    @Test
    @DisplayName("종료 제약이 있으면 최종일 도착 위치 override를 허용하지 않는다")
    void rejectsFinalLocationOverrideWhenEndConstraintExists() throws Exception {
        String request = """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-10",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "endConstraint":{
                    "type":"TRAIN_DEPARTURE",
                    "location":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                    "targetAt":"2026-08-10T20:00:00+09:00"
                  },
                  "dayOverrides":[{
                    "date":"2026-08-10",
                    "endLocation":{"name":"광안리","longitude":129.1186,"latitude":35.1532}
                  }],
                  "selectedAnswers":%s
                }
                """.formatted(selectedAnswers());

        mockMvc.perform(post("/api/v1/schedule-previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_PREVIEW_REQUEST"));
    }

    private String undecidedPreviewRequest(long mustVisitPlaceId) {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-12",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "startTime":"10:00",
                  "lodgingPlan":{"mode":"UNDECIDED"},
                  "endConstraint":{
                    "type":"FLIGHT_DEPARTURE",
                    "location":{"name":"김해국제공항","longitude":128.9485,"latitude":35.1732},
                    "targetAt":"2026-08-12T20:00:00+09:00"
                  },
                  "selectedAnswers":%s,
                  "mustVisitPlaceIds":[%d],
                  "customPrompt":"바다를 많이 보고 걷는 구간은 적었으면 좋겠어요"
                }
                """.formatted(selectedAnswers(), mustVisitPlaceId);
    }

    private String minimalPreviewRequest(String answers) {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-10",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "lodgingPlan":{"mode":"UNDECIDED"},
                  "selectedAnswers":%s
                }
                """.formatted(answers);
    }

    private String fixedEventConflictRequest(long firstPlaceId, long secondPlaceId) {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-10",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "startTime":"10:00",
                  "lodgingPlan":{"mode":"UNDECIDED"},
                  "selectedAnswers":%s,
                  "fixedEvents":[
                    {"clientEventId":"event-1","name":"공연 1","placeId":%d,
                     "startsAt":"2026-08-10T14:00:00+09:00","endsAt":"2026-08-10T16:00:00+09:00"},
                    {"clientEventId":"event-2","name":"공연 2","placeId":%d,
                     "startsAt":"2026-08-10T15:00:00+09:00","endsAt":"2026-08-10T17:00:00+09:00"}
                  ]
                }
                """.formatted(selectedAnswers(), firstPlaceId, secondPlaceId);
    }

    private String fixedEventRequest(long eventPlaceId) {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-10",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "startTime":"10:00",
                  "lodgingPlan":{"mode":"UNDECIDED"},
                  "selectedAnswers":%s,
                  "fixedEvents":[
                    {"clientEventId":"event-evening","name":"저녁 공연","placeId":%d,
                     "startsAt":"2026-08-10T18:00:00+09:00","endsAt":"2026-08-10T19:00:00+09:00"}
                  ]
                }
                """.formatted(selectedAnswers(), eventPlaceId);
    }

    private String fixedBasePreviewRequest() {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-11",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "startTime":"10:00",
                  "lodgingPlan":{
                    "mode":"FIXED_BASE",
                    "baseLocation":{"name":"해운대 숙소","longitude":129.158,"latitude":35.159}
                  },
                  "selectedAnswers":%s
                }
                """.formatted(selectedAnswers());
    }

    private String perNightPreviewRequest() {
        return """
                {
                  "startDate":"2026-08-10",
                  "endDate":"2026-08-12",
                  "startLocation":{"name":"부산역","longitude":129.0403,"latitude":35.1151},
                  "startTime":"10:00",
                  "lodgingPlan":{
                    "mode":"PER_NIGHT",
                    "nightStays":[
                      {"date":"2026-08-10","location":{"name":"해운대 숙소","longitude":129.158,"latitude":35.159}},
                      {"date":"2026-08-11","location":{"name":"남포동 숙소","longitude":129.032,"latitude":35.100}}
                    ]
                  },
                  "selectedAnswers":%s
                }
                """.formatted(selectedAnswers());
    }

    private String selectedAnswers() {
        return """
                [
                  {"questionId":"COMPANION","answerIds":["COMPANION_FRIENDS"]},
                  {"questionId":"PACE","answerIds":["PACE_RELAXED"]},
                  {"questionId":"THEME","answerIds":["THEME_FOOD","THEME_NATURE"]},
                  {"questionId":"MOBILITY","answerIds":["MOBILITY_NORMAL"]},
                  {"questionId":"TRANSIT","answerIds":["TRANSIT_SIMPLE"]}
                ]
                """;
    }

    private Place place(String externalId, String name, String longitude, String latitude) {
        return new Place(
                "TOUR_API", externalId, "12", name, "관광지", "부산",
                new BigDecimal(longitude), new BigDecimal(latitude), null);
    }
}
