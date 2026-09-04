package com.server.spontaneous;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.GlobalExceptionHandler;
import com.server.common.web.TraceIdFilter;
import com.server.external.spontaneous.FastApiSpontaneousClient;
import com.server.spontaneous.dto.CourseRole;
import com.server.spontaneous.dto.CourseStop;
import com.server.spontaneous.dto.DestinationRecommendation;
import com.server.spontaneous.dto.SpontaneousCourseRequest;
import com.server.spontaneous.dto.SpontaneousCourseResponse;
import com.server.spontaneous.dto.SpontaneousDestinationRequest;
import com.server.spontaneous.dto.SpontaneousDestinationResponse;
import com.server.spontaneous.dto.TransportMode;
import com.server.spontaneous.dto.TransportSummary;
import com.server.spontaneous.dto.TravelTheme;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("즉흥여행 API 부산 출발지 경계 검증")
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
        when(fastApiSpontaneousClient.recommendCourse(any(SpontaneousCourseRequest.class)))
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
        verify(fastApiSpontaneousClient).recommendCourse(any(SpontaneousCourseRequest.class));
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
