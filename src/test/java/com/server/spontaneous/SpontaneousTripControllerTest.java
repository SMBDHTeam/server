package com.server.spontaneous;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.auth.service.AuthenticatedUser;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.external.spontaneous.FastApiSpontaneousClient;
import com.server.schedule.dto.ScheduleResponse;
import com.server.spontaneous.dto.CourseRole;
import com.server.spontaneous.dto.CourseStop;
import com.server.spontaneous.dto.DestinationRecommendation;
import com.server.spontaneous.dto.SpontaneousCourseRequest;
import com.server.spontaneous.dto.SpontaneousCourseResponse;
import com.server.spontaneous.dto.SpontaneousDestinationRequest;
import com.server.spontaneous.dto.SpontaneousDestinationResponse;
import com.server.spontaneous.dto.SpontaneousScheduleRequest;
import com.server.spontaneous.dto.TransportMode;
import com.server.spontaneous.dto.TransportSummary;
import com.server.spontaneous.dto.TravelTheme;
import com.server.user.domain.UserRole;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("즉흥여행 API 부산 출발지 경계 검증과 오류 응답")
class SpontaneousTripControllerTest {

    private final FastApiSpontaneousClient fastApiSpontaneousClient = mock(FastApiSpontaneousClient.class);
    private final SpontaneousStartLocationValidator startLocationValidator =
            mock(SpontaneousStartLocationValidator.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SpontaneousTripController(
                    fastApiSpontaneousClient,
                    startLocationValidator
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new TraceIdFilter())
            .build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("/destinations 부산 밖 출발지는 FastAPI 호출 없이 400으로 차단한다")
    void destinationsOutsideBusanDoesNotCallFastApi() throws Exception {
        doThrow(new BusinessException(ErrorCode.SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN))
                .when(startLocationValidator).validateBusan(any());

        mockMvc.perform(post("/api/v1/spontaneous-trips/destinations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destinationRequestJson(37.5665, 126.9780)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN"))
                .andExpect(jsonPath("$.message")
                        .value("즉흥여행 출발지는 부산광역시 내에서 선택해 주세요."));

        verifyNoInteractions(fastApiSpontaneousClient);
    }

    @Test
    @DisplayName("/course 부산 밖 출발지는 FastAPI 호출 없이 400으로 차단한다")
    void courseOutsideBusanDoesNotCallFastApi() throws Exception {
        doThrow(new BusinessException(ErrorCode.SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN))
                .when(startLocationValidator).validateBusan(any());

        mockMvc.perform(post("/api/v1/spontaneous-trips/course")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(37.5665, 126.9780)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN"));

        verifyNoInteractions(fastApiSpontaneousClient);
    }

    @Test
    @DisplayName("부산 출발지는 검증 후 FastAPI에 정상 전달한다")
    void busanStartLocationCallsFastApiOnce() throws Exception {
        when(fastApiSpontaneousClient.recommendDestinations(any(SpontaneousDestinationRequest.class)))
                .thenReturn(new SpontaneousDestinationResponse(List.of(new DestinationRecommendation(
                        "BUSAN_GWANGALLI",
                        "광안리·민락",
                        1.0,
                        1200,
                        new TransportSummary(TransportMode.CAR, 12, 15, 260)
                ))));
        when(fastApiSpontaneousClient.recommendCourse(
                any(SpontaneousCourseRequest.class), isNull()))
                .thenReturn(new SpontaneousCourseResponse(
                        "BUSAN_GWANGALLI",
                        "광안리·민락",
                        TransportMode.CAR,
                        15,
                        OffsetDateTime.parse("2026-09-03T20:15:00+09:00"),
                        OffsetDateTime.parse("2026-09-03T23:00:00+09:00"),
                        List.of(new CourseStop(
                                1,
                                CourseRole.ACTIVITY,
                                "광안리해변 테마거리",
                                "127925",
                                "12",
                                35.1551657503,
                                129.1221363273,
                                12,
                                OffsetDateTime.parse("2026-09-03T18:12:00+09:00"),
                                OffsetDateTime.parse("2026-09-03T19:12:00+09:00"),
                                60,
                                List.of(TravelTheme.SEA)
                        ))
                ));

        mockMvc.perform(post("/api/v1/spontaneous-trips/destinations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destinationRequestJson(35.1151, 129.0403)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinations[0].destinationId").value("BUSAN_GWANGALLI"));

        mockMvc.perform(post("/api/v1/spontaneous-trips/course")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(35.1151, 129.0403)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationId").value("BUSAN_GWANGALLI"));

        verify(fastApiSpontaneousClient).recommendDestinations(any(SpontaneousDestinationRequest.class));
        verify(fastApiSpontaneousClient).recommendCourse(
                any(SpontaneousCourseRequest.class), isNull());
    }

    @Test
    @DisplayName("/schedules는 토큰의 현재 사용자와 멱등성 키를 FastAPI에 전달한다")
    void saveScheduleForwardsAuthenticatedOwnerAndIdempotencyKey() throws Exception {
        UUID previewId = UUID.fromString("d9f1121a-33e1-4c77-9c96-e0ca35a268f0");
        String previewToken = "signed-preview-token-with-more-than-thirty-two-characters";
        ScheduleResponse saved = new ScheduleResponse(
                UUID.fromString("b67b650a-4605-454d-859d-e434729ff3f2"),
                "CONFIRMED",
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12),
                null,
                null,
                "즉흥여행 · 광안리·민락",
                List.of(),
                null,
                null,
                null,
                "SPONTANEOUS",
                "PUBLIC_TRANSIT",
                OffsetDateTime.parse("2026-09-11T20:30:00+09:00"),
                OffsetDateTime.parse("2026-09-12T01:00:00+09:00"),
                OffsetDateTime.parse("2026-09-12T00:42:00+09:00"),
                java.util.Map.of("schemaVersion", 1)
        );
        when(fastApiSpontaneousClient.saveSchedule(
                eq(new SpontaneousScheduleRequest(previewId, previewToken)),
                eq("save-once"),
                eq(42L))).thenReturn(saved);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(42L, UserRole.USER), null, List.of()));

        mockMvc.perform(post("/api/v1/spontaneous-trips/schedules")
                        .header("Idempotency-Key", "save-once")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "previewId": "%s",
                                  "previewToken": "%s"
                                }
                                """.formatted(previewId, previewToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("b67b650a-4605-454d-859d-e434729ff3f2"))
                .andExpect(jsonPath("$.scheduleType").value("SPONTANEOUS"));

        verify(fastApiSpontaneousClient).saveSchedule(
                new SpontaneousScheduleRequest(previewId, previewToken), "save-once", 42L);
    }

    @Test
    @DisplayName("/schedules의 멱등성 키가 없으면 명시적인 400 오류를 반환한다")
    void saveScheduleRequiresIdempotencyKey() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(42L, UserRole.USER), null, List.of()));

        mockMvc.perform(post("/api/v1/spontaneous-trips/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "previewId": "d9f1121a-33e1-4c77-9c96-e0ca35a268f0",
                                  "previewToken": "signed-preview-token-with-more-than-thirty-two-characters"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verifyNoInteractions(fastApiSpontaneousClient);
    }

    @ParameterizedTest
    @MethodSource("spontaneousErrors")
    @DisplayName("/destinations 오류는 공통 응답 형식과 현재 한국어 메시지를 유지한다")
    void destinationsErrorResponseUsesKoreanMessage(
            ErrorCode errorCode, int httpStatus, String message
    ) throws Exception {
        when(fastApiSpontaneousClient.recommendDestinations(any(SpontaneousDestinationRequest.class)))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/api/v1/spontaneous-trips/destinations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destinationRequestJson(35.1151, 129.0403)))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.detail").doesNotExist());

        verify(fastApiSpontaneousClient).recommendDestinations(any(SpontaneousDestinationRequest.class));
    }

    @ParameterizedTest
    @MethodSource("spontaneousErrors")
    @DisplayName("/course 오류는 공통 응답 형식과 현재 한국어 메시지를 유지한다")
    void courseErrorResponseUsesKoreanMessage(
            ErrorCode errorCode, int httpStatus, String message
    ) throws Exception {
        when(fastApiSpontaneousClient.recommendCourse(
                any(SpontaneousCourseRequest.class), isNull()))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/api/v1/spontaneous-trips/course")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseRequestJson(35.1151, 129.0403)))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.detail").doesNotExist());

        verify(fastApiSpontaneousClient).recommendCourse(
                any(SpontaneousCourseRequest.class), isNull());
    }

    private static Stream<Arguments> spontaneousErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.INVALID_SPONTANEOUS_TRIP_REQUEST, 400,
                        "즉흥여행 요청 조건이 올바르지 않습니다."),
                Arguments.of(ErrorCode.SPONTANEOUS_DESTINATION_NOT_FOUND, 404,
                        "선택한 즉흥여행 목적지를 찾을 수 없습니다."),
                Arguments.of(ErrorCode.SPONTANEOUS_DESTINATIONS_NOT_FOUND, 404,
                        "현재 조건에 맞는 즉흥여행 목적지를 찾을 수 없습니다. 여행 시간이나 테마를 변경해 주세요."),
                Arguments.of(ErrorCode.SPONTANEOUS_COURSE_NOT_FEASIBLE, 422,
                        "선택한 조건으로 가능한 즉흥여행 코스를 만들 수 없습니다. 여행 시간이나 테마를 변경해 주세요."),
                Arguments.of(ErrorCode.SPONTANEOUS_ROUTE_NOT_FOUND, 422,
                        "선택한 조건으로 이동 가능한 경로를 찾을 수 없습니다."),
                Arguments.of(ErrorCode.SPONTANEOUS_PROVIDER_ERROR, 502,
                        "여행 정보 제공 서비스 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
                Arguments.of(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE, 503,
                        "여행 정보 제공 서비스를 현재 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.")
        );
    }

    private String destinationRequestJson(double latitude, double longitude) {
        return """
                {
                  "startLocation": {"latitude": %s, "longitude": %s},
                  "startAt": "2026-09-03T18:00:00+09:00",
                  "returnBy": "2026-09-03T23:00:00+09:00",
                  "transportMode": "CAR",
                  "desiredThemes": ["SEA"]
                }
                """.formatted(latitude, longitude);
    }

    private String courseRequestJson(double latitude, double longitude) {
        return """
                {
                  "destinationId": "BUSAN_GWANGALLI",
                  "startLocation": {"latitude": %s, "longitude": %s},
                  "startAt": "2026-09-03T18:00:00+09:00",
                  "returnBy": "2026-09-03T23:00:00+09:00",
                  "transportMode": "CAR",
                  "desiredThemes": ["SEA"]
                }
                """.formatted(latitude, longitude);
    }
}
