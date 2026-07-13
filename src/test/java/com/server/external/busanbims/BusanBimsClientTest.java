package com.server.external.busanbims;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("부산 BIMS 클라이언트")
class BusanBimsClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("정류장 ID 도착정보 공식 경로를 호출하고 가장 빠른 동일 노선을 반환한다")
    void findArrivalUsesStopArrivalEndpoint() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stopArrByBstopid", this::handleArrivalRequest);
        server.start();

        BusanBimsClient client = createClient("test-service-key");

        Optional<BusanBimsArrivalEstimate> result = client.findArrival("505780000", "1001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().waitMinutes()).isEqualTo(12);
        assertThat(result.orElseThrow().routeName()).isEqualTo("1001");
    }

    private BusanBimsClient createClient(String serviceKey) {
        BusanBimsProperties properties = new BusanBimsProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                serviceKey,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        BusanBimsConfig config = new BusanBimsConfig();
        return new BusanBimsClient(config.busanBimsRestClient(properties), properties);
    }

    private void handleArrivalRequest(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestURI().getPath()).isEqualTo("/stopArrByBstopid");
        assertThat(exchange.getRequestURI().getQuery())
                .contains("serviceKey=test-service-key", "bstopid=505780000", "resultType=json");

        byte[] responseBody = """
                <response>
                  <body>
                    <items>
                      <item><lineNo>1001</lineNo><min1>12</min1></item>
                      <item><lineNo>40</lineNo><min1>3</min1></item>
                    </items>
                  </body>
                </response>
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }
}
