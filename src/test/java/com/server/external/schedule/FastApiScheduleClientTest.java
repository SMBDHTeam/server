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

/**
 * 일정 API가 FastAPI에 위임되면서, Spring에 남은 책임은 요청 전달과 오류 코드 매핑이다.
 * Planner 동작을 검증하던 E2E 테스트를 대신해 이 계층을 고정한다.
 */
@DisplayName("FastAPI 일정 위임 클라이언트")
class FastApiScheduleClientTest {

    private static final String BASE_URL = "http://data-ai:8010";
    private static final UUID ZERO = UUID.fromString("00000000-0000-0000-0000-000000000000");

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

    private ErrorCode errorCodeOf(Throwable throwable) {
        return ((BusinessException) throwable).getErrorCode();
    }

    @Test
    @DisplayName("Preview 기반 생성은 Idempotency-Key 헤더를 그대로 전달한다")
    void createFromPreviewForwardsIdempotencyKey() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(BASE_URL + "/api/v1/schedules"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andRespond(withSuccess("""
                        {"id":"%s","status":"CONFIRMED","startDate":"2026-06-23",
                         "endDate":"2026-06-23","styleSummary":"요약","days":[]}
                        """.formatted(ZERO), MediaType.APPLICATION_JSON));

        var response = fixture.client()
                .createScheduleFromPreview(new SchedulePreviewScheduleRequest(ZERO), "key-1", null);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        fixture.server().verify();
    }

    @Test
    @DisplayName("일정 404는 SCHEDULE_NOT_FOUND로, Preview 404는 SCHEDULE_PREVIEW_NOT_FOUND로 변환한다")
    void notFoundMapsPerResource() {
        Fixture schedule = fixture();
        schedule.server().expect(requestTo(BASE_URL + "/api/v1/schedules/" + ZERO))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> schedule.client().getSchedule(ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);

        Fixture preview = fixture();
        preview.server().expect(requestTo(BASE_URL + "/api/v1/schedule-previews/" + ZERO))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> preview.client().getPreview(ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting(this::errorCodeOf)
                .isEqualTo(ErrorCode.SCHEDULE_PREVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("410은 Preview 만료로 변환한다")
    void goneMapsToPreviewExpired() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(BASE_URL + "/api/v1/schedule-previews/" + ZERO))
                .andRespond(withStatus(HttpStatus.GONE));

        assertThatThrownBy(() -> fixture.client().getPreview(ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting(this::errorCodeOf)
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
                .createScheduleFromPreview(new SchedulePreviewScheduleRequest(ZERO), "key-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(this::errorCodeOf)
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
                .extracting(this::errorCodeOf)
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("위임이 꺼져 있으면 enabled가 false다")
    void disabledClientReportsDisabled() {
        FastApiScheduleProperties properties = new FastApiScheduleProperties(
                false, BASE_URL, Duration.ofSeconds(3), Duration.ofSeconds(15));
        FastApiScheduleClient client = new FastApiScheduleClient(
                RestClient.builder().baseUrl(BASE_URL).build(), properties, new ObjectMapper());

        assertThat(client.enabled()).isFalse();
    }
}
