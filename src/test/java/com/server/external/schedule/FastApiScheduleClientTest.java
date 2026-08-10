package com.server.external.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.schedule.dto.SchedulePreviewScheduleRequest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("FastAPI 일정 위임 클라이언트")
class FastApiScheduleClientTest {

    private static final String BASE_URL = "http://data-ai:8010";

    private record Fixture(FastApiScheduleClient client, MockRestServiceServer server) { }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FastApiScheduleProperties properties = new FastApiScheduleProperties(
                true, BASE_URL, Duration.ofSeconds(3), Duration.ofSeconds(15));
        return new Fixture(
                new FastApiScheduleClient(builder.build(), properties, new ObjectMapper()),
                server);
    }

    @Test
    @DisplayName("Preview 기반 생성은 Idempotency-Key를 그대로 전달한다")
    void createFromPreviewForwardsIdempotencyKey() {
        Fixture fixture = fixture();
        UUID previewId = UUID.randomUUID();
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/schedules"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"CONFIRMED","startDate":"2026-06-23",
                         "endDate":"2026-06-23","styleSummary":"요약","days":[]}
                        """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

        var response = fixture.client()
                .createScheduleFromPreview(new SchedulePreviewScheduleRequest(previewId), "key-1");

        assertThat(response.status()).isEqualTo("CONFIRMED");
        fixture.server().verify();
    }

    @Test
    @DisplayName("404는 일정을 찾을 수 없음으로 변환한다")
    void notFoundMapsToScheduleNotFound() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/schedules/" + ZERO))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"detail\":\"Schedule not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().getSchedule(ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("410은 Preview 만료로 변환한다")
    void goneMapsToPreviewExpired() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/schedule-previews/" + ZERO))
                .andRespond(withStatus(HttpStatus.GONE)
                        .body("{\"detail\":\"Preview expired\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().getPreview(ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PREVIEW_EXPIRED);
    }

    @Test
    @DisplayName("409는 응답 본문에 따라 서로 다른 충돌 코드로 변환한다")
    void conflictMapsByResponseBody() {
        assertConflictMaps("creation already in progress", ErrorCode.SCHEDULE_CREATION_IN_PROGRESS);
        assertConflictMaps("preview already consumed", ErrorCode.PREVIEW_ALREADY_CONSUMED);
        assertConflictMaps("duplicate request", ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    private void assertConflictMaps(String detail, ErrorCode expected) {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/schedules"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("{\"detail\":\"%s\"}".formatted(detail))
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client()
                .createScheduleFromPreview(new SchedulePreviewScheduleRequest(ZERO), "key-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("매핑하지 않은 상태 코드는 외부 서비스 불가로 변환한다")
    void unmappedStatusMapsToProviderUnavailable() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/schedules/" + ZERO))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> fixture.client().getSchedule(ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("enabled가 꺼져 있으면 위임을 시도하지 않는다")
    void disabledClientReportsDisabled() {
        FastApiScheduleProperties properties = new FastApiScheduleProperties(
                false, BASE_URL, Duration.ofSeconds(3), Duration.ofSeconds(15));
        FastApiScheduleClient client = new FastApiScheduleClient(
                RestClient.builder().baseUrl(BASE_URL).build(), properties, new ObjectMapper());

        assertThat(client.enabled()).isFalse();
    }

    private static final UUID ZERO = UUID.fromString("00000000-0000-0000-0000-000000000000");
}
