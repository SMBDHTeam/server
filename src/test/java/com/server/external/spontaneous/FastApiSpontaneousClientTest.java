package com.server.external.spontaneous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.spontaneous.dto.Coordinate;
import com.server.spontaneous.dto.SpontaneousCourseRequest;
import com.server.spontaneous.dto.SpontaneousDestinationRequest;
import com.server.spontaneous.dto.TransportMode;
import com.server.spontaneous.dto.TravelTheme;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("FastAPI spontaneous client")
class FastApiSpontaneousClientTest {

    private static final String BASE_URL = "http://data-ai:8010";
    private static final OffsetDateTime START_AT = OffsetDateTime.parse("2026-09-03T18:00:00+09:00");
    private static final OffsetDateTime RETURN_BY = OffsetDateTime.parse("2026-09-03T23:00:00+09:00");

    private record Fixture(FastApiSpontaneousClient client, MockRestServiceServer server) { }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
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

        var response = fixture.client().recommendCourse(courseRequest());

        assertThat(response.transportMode()).isEqualTo(TransportMode.CAR);
        assertThat(response.estimatedReturnAt()).isEqualTo(OffsetDateTime.parse("2026-09-03T21:30:00+09:00"));
        assertThat(response.course()).hasSize(1);
        assertThat(response.course().get(0).themes()).containsExactly(TravelTheme.SEA, TravelTheme.WALK);
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
        assertCourseError("ODSAY_AUTH_FAILED", HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
        assertCourseError("ODSAY_QUOTA_EXCEEDED", HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
        assertCourseError("TOUR_API_ERROR", HttpStatus.BAD_GATEWAY,
                ErrorCode.SPONTANEOUS_PROVIDER_ERROR);
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
    }
}
