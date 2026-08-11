package com.server.external.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * FastAPI 위임 RestClient가 실제로 낼 수 있는 HTTP 메서드를 검증한다.
 *
 * <p>{@code PATCH /api/v1/schedules/{id}} 는 요청 형태가 맞아도 배포 서버에서
 * 즉시 503으로 떨어졌다. 원인은 요청 팩토리였다. 조회·생성만 검증하는 테스트로는
 * 이 결함이 드러나지 않으므로 메서드별로 확인한다.
 */
@DisplayName("FastAPI 위임 RestClient")
class FastApiScheduleConfigTest {

    private HttpServer server;
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedProtocol = new AtomicReference<>();
    private final AtomicReference<List<String>> receivedUpgradeHeaders =
            new AtomicReference<>(List.of());

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedProtocol.set(exchange.getProtocol());
            receivedUpgradeHeaders.set(exchange.getRequestHeaders().keySet().stream()
                    .filter(name -> name.equalsIgnoreCase("Upgrade")
                            || name.equalsIgnoreCase("HTTP2-Settings"))
                    .toList());
            receivedBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private RestClient client() {
        FastApiScheduleProperties properties = new FastApiScheduleProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(15));
        return new FastApiScheduleConfig().fastApiScheduleRestClient(properties);
    }

    @Test
    @DisplayName("PATCH 요청을 본문과 함께 실제로 전송한다")
    void sendsPatch() {
        assertThatCode(() -> client().patch()
                .uri("/api/v1/schedules/{id}", "a")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"stops\":[]}")
                .retrieve()
                .body(String.class))
                .doesNotThrowAnyException();

        assertThat(receivedMethod.get()).isEqualTo("PATCH");
        // 메서드만 확인하면 본문이 통째로 빠져도 통과한다. FastAPI 는 본문이 없으면
        // loc=["body"] "Field required" 로 422 를 낸다.
        assertThat(receivedBody.get()).isEqualTo("{\"stops\":[]}");
    }

    @Test
    @DisplayName("HTTP/1.1 로 보내고 h2c 업그레이드를 시도하지 않는다")
    void doesNotAttemptHttp2Upgrade() {
        client().post()
                .uri("/api/v1/schedule-previews")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"startDate\":\"2026-09-20\"}")
                .retrieve()
                .body(String.class);

        // HttpClient 기본값인 HTTP/2 로 두면 평문 연결에서 h2c 업그레이드를 시도한다.
        // uvicorn 은 이를 지원하지 않고, 거부되는 과정에서 본문이 유실돼
        // 본문 있는 POST·PATCH 가 전부 422 로 떨어진다.
        assertThat(receivedProtocol.get()).isEqualTo("HTTP/1.1");
        assertThat(receivedUpgradeHeaders.get()).isEmpty();
        assertThat(receivedBody.get()).isEqualTo("{\"startDate\":\"2026-09-20\"}");
    }

    @Test
    @DisplayName("GET과 POST도 그대로 전송한다")
    void sendsGetAndPost() {
        client().get().uri("/api/v1/schedules").retrieve().body(String.class);
        assertThat(receivedMethod.get()).isEqualTo("GET");

        client().post()
                .uri("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(String.class);
        assertThat(receivedMethod.get()).isEqualTo("POST");
    }
}
