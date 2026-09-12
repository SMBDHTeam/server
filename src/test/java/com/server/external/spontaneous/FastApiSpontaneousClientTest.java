package com.server.external.spontaneous;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.SpontaneousPreviewAlreadySavedException;
import com.server.spontaneous.dto.Coordinate;
import com.server.spontaneous.dto.SpontaneousCourseRequest;
import com.server.spontaneous.dto.SpontaneousDestinationRequest;
import com.server.spontaneous.dto.SpontaneousScheduleRequest;
import com.server.spontaneous.dto.TransportMode;
import com.server.spontaneous.dto.TravelTheme;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("FastAPI spontaneous client")
class FastApiSpontaneousClientTest {

    private static final String BASE_URL = "http://data-ai:8010";
    private static final OffsetDateTime START_AT = OffsetDateTime.parse("2026-09-03T18:00:00+09:00");
    private static final OffsetDateTime RETURN_BY = OffsetDateTime.parse("2026-09-03T23:00:00+09:00");

    private record Fixture(FastApiSpontaneousClient client, MockRestServiceServer server) { }

    private Fixture fixture() {
        var objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .messageConverters(converters -> {
                    converters.removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
                    converters.add(new JacksonJsonHttpMessageConverter(objectMapper));
                });
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiSpontaneousProperties properties = new FastApiSpontaneousProperties(
                true, BASE_URL, Duration.ofSeconds(3), Duration.ofSeconds(15));
        return new Fixture(
                new FastApiSpontaneousClient(builder.build(), properties, new ObjectMapper()),
                server);
    }

    private SpontaneousDestinationRequest destinationRequest() {
        return new SpontaneousDestinationRequest(
                new Coordinate(35.1578, 129.0592),
                START_AT,
                RETURN_BY,
                TransportMode.CAR,
                List.of(TravelTheme.SEA, TravelTheme.SEAFOOD)
        );
    }

    private SpontaneousCourseRequest courseRequest() {
        return new SpontaneousCourseRequest(
                "BUSAN_GWANGALLI",
                new Coordinate(35.1578, 129.0592),
                START_AT,
                RETURN_BY,
                TransportMode.CAR,
                List.of(TravelTheme.SEA, TravelTheme.SEAFOOD)
        );
    }

    private ErrorCode errorCodeOf(Throwable throwable) {
        return ((BusinessException) throwable).getErrorCode();
    }

    @Test
    @DisplayName("destination response uses typed public DTO")
    void destinationResponseUsesTypedPublicDto() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/spontaneous-trips/destinations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"destinations":[{"destinationId":"BUSAN_GWANGALLI","name":"Gwangalli",
                        "themeScore":1.0,"distanceMeters":5433,"score":0.99,
                        "transport":{"mode":"CAR","available":true,"outboundMinutes":16,
                        "returnMinutes":19,"availableStayMinutes":265,
                        "expectedReturnAt":null,"unavailableReason":null}}]}
                        """, MediaType.APPLICATION_JSON));

        var response = fixture.client().recommendDestinations(destinationRequest());

        assertThat(response.destinations()).hasSize(1);
        assertThat(response.destinations().get(0).transport().mode()).isEqualTo(TransportMode.CAR);
        assertThat(response.destinations().get(0).transport().outboundMinutes()).isEqualTo(16);
        fixture.server().verify();
    }

    @Test
    @DisplayName("course response uses typed public DTO")
    void courseResponseUsesTypedPublicDto() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/spontaneous-trips/course"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Auth-User-Id", "42"))
                .andRespond(withSuccess("""
                        {"destinationId":"BUSAN_GWANGALLI","name":"Gwangalli","transportMode":"CAR",
                        "transport":{"mode":"CAR"},"returnTravelMinutes":19,"finalReturnMinutes":19,
                        "estimatedReturnAt":"2026-09-03T21:30:00+09:00",
                        "expectedReturnAt":"2026-09-03T21:30:00+09:00",
                        "returnBy":"2026-09-03T23:00:00+09:00","candidateCounts":{"searched":10},
                        "course":[{"order":1,"role":"ACTIVITY","name":"Beach","contentId":"123",
                        "contentTypeId":"12","latitude":35.0,"longitude":129.0,
                        "travelMinutesFromPrevious":10,"arrivalAt":"2026-09-03T18:10:00+09:00",
                        "departureAt":"2026-09-03T19:10:00+09:00","returnTravelMinutes":19,
                        "inboundMinutes":19,"arriveAt":"2026-09-03T18:10:00+09:00",
                        "departAt":"2026-09-03T19:10:00+09:00","stayMinutes":60,
                        "themes":["SEA","WALK"],"score":0.8}]}
                        """, MediaType.APPLICATION_JSON));

        var response = fixture.client().recommendCourse(courseRequest(), 42L);

        assertThat(response.transportMode()).isEqualTo(TransportMode.CAR);
        assertThat(response.estimatedReturnAt()).isEqualTo(OffsetDateTime.parse("2026-09-03T21:30:00+09:00"));
        assertThat(response.course()).hasSize(1);
        assertThat(response.course().get(0).themes()).containsExactly(TravelTheme.SEA, TravelTheme.WALK);
        fixture.server().verify();
    }

    @Test
    @DisplayName("save forwards the token owner and idempotency key and reads common schedule DTO")
    void saveForwardsTrustedHeadersAndReadsScheduleResponse() {
        Fixture fixture = fixture();
        UUID previewId = UUID.fromString("1c50fc08-3dce-49f6-9fd0-106e7308428a");
        UUID scheduleId = UUID.fromString("f2536c52-69d1-4e6c-8ab6-2ede45dba2cd");
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/spontaneous-trips/schedules"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "save-once"))
                .andExpect(header("X-Auth-User-Id", "42"))
                .andRespond(withSuccess("""
                        {"id":"f2536c52-69d1-4e6c-8ab6-2ede45dba2cd","status":"CONFIRMED",
                        "startDate":"2026-09-03","endDate":"2026-09-04",
                        "dailyStartTime":"23:00:00","dailyEndTime":"01:30:00",
                        "styleSummary":"spontaneous","days":[],"scheduleType":"SPONTANEOUS",
                        "transportMode":"CAR","startAt":"2026-09-03T23:00:00+09:00",
                        "returnBy":"2026-09-04T02:00:00+09:00",
                        "estimatedReturnAt":"2026-09-04T01:30:00+09:00"}
                        """, MediaType.APPLICATION_JSON));

        var response = fixture.client().saveSchedule(
                new SpontaneousScheduleRequest(previewId, "signed-preview-token-with-more-than-thirty-two-characters"),
                "save-once", 42L);

        assertThat(response.id()).isEqualTo(scheduleId);
        assertThat(response.scheduleType()).isEqualTo("SPONTANEOUS");
        assertThat(response.estimatedReturnAt().getDayOfMonth()).isEqualTo(4);
        fixture.server().verify();
    }

    @Test
    @DisplayName("save validation failures do not log the rejected preview token")
    void saveValidationFailureDoesNotLogPreviewToken() {
        Fixture fixture = fixture();
        UUID previewId = UUID.fromString("1c50fc08-3dce-49f6-9fd0-106e7308428a");
        String previewToken = "signed-preview-token-that-must-never-appear-in-server-logs";
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/spontaneous-trips/schedules"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("""
                                {"detail":[{"type":"string_too_short","loc":["body","previewToken"],
                                "msg":"String should have at least 32 characters","input":"%s"}]}
                                """.formatted(previewToken))
                        .contentType(MediaType.APPLICATION_JSON));

        Logger logger = (Logger) LoggerFactory.getLogger(FastApiSpontaneousClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> fixture.client().saveSchedule(
                    new SpontaneousScheduleRequest(previewId, previewToken), "save-once", 42L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(this::errorCodeOf)
                    .isEqualTo(ErrorCode.INVALID_SPONTANEOUS_TRIP_REQUEST);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(previewToken));
        fixture.server().verify();
    }

    @Test
    @DisplayName("already-saved preview exposes the existing schedule ID")
    void alreadySavedPreviewExposesScheduleId() {
        Fixture fixture = fixture();
        UUID previewId = UUID.fromString("1c50fc08-3dce-49f6-9fd0-106e7308428a");
        UUID scheduleId = UUID.fromString("f2536c52-69d1-4e6c-8ab6-2ede45dba2cd");
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/spontaneous-trips/schedules"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .header("X-Schedule-Id", scheduleId.toString())
                        .body("""
                                {"detail":"SPONTANEOUS_PREVIEW_ALREADY_SAVED"}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().saveSchedule(
                new SpontaneousScheduleRequest(
                        previewId,
                        "signed-preview-token-with-more-than-thirty-two-characters"),
                "different-key",
                42L))
                .isInstanceOfSatisfying(
                        SpontaneousPreviewAlreadySavedException.class,
                        exception -> assertThat(exception.getScheduleId()).isEqualTo(scheduleId));
        fixture.server().verify();
    }

    @Test
    @DisplayName("FastAPI business detail maps to spontaneous ErrorCode")
    void fastApiBusinessDetailMapsToErrorCode() {
        assertCourseError("COURSE_NOT_FEASIBLE", HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.SPONTANEOUS_COURSE_NOT_FEASIBLE);
        assertCourseError("NO_ROUTE", HttpStatus.UNPROCESSABLE_ENTITY,
                ErrorCode.SPONTANEOUS_ROUTE_NOT_FOUND);
        assertCourseError("DESTINATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                ErrorCode.SPONTANEOUS_DESTINATION_NOT_FOUND);
    }

    @Test
    @DisplayName("provider auth and quota are not exposed as user auth errors")
    void providerAuthAndQuotaMapToProviderUnavailable() {
        assertCourseError("TOUR_API_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
        assertCourseError("ODSAY_AUTH_FAILED", HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
        assertCourseError("ODSAY_QUOTA_EXCEEDED", HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
        assertCourseError("TOUR_API_ERROR", HttpStatus.BAD_GATEWAY,
                ErrorCode.SPONTANEOUS_PROVIDER_ERROR);
    }

    @Test
    @DisplayName("TMAP quota exceeded maps to provider unavailable")
    void tmapQuotaExceededMapsToProviderUnavailable() {
        assertCourseError("TMAP_QUOTA_EXCEEDED", HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("TMAP quota detail takes precedence over the HTTP status fallback")
    void tmapQuotaDetailTakesPrecedenceOverHttpStatus() {
        assertCourseError("TMAP_QUOTA_EXCEEDED", HttpStatus.BAD_GATEWAY,
                ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("destination TMAP quota exceeded maps to provider unavailable")
    void destinationTmapQuotaExceededMapsToProviderUnavailable() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/spontaneous-trips/destinations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"detail\":\"TMAP_QUOTA_EXCEEDED\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().recommendDestinations(destinationRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
        fixture.server().verify();
    }

    private void assertCourseError(String detail, HttpStatus status, ErrorCode expected) {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/spontaneous-trips/course"))
                .andRespond(withStatus(status)
                        .body("{\"detail\":\"%s\"}".formatted(detail))
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().recommendCourse(courseRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo(expected);
        fixture.server().verify();
    }
}
