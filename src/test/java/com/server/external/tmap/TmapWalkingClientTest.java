package com.server.external.tmap;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TMAP 도보 경로 클라이언트")
class TmapWalkingClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("보행자 경로 응답의 LineString 좌표를 추출한다")
    void findWalkingRouteCoordinatesExtractsLineStringCoordinates() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/tmap/routes/pedestrian", this::handlePedestrianRoute);
        server.start();

        TmapWalkingClient client = createClient("test-api-key");

        var route = client.findWalkingRoute(
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                new BigDecimal("129.032338"),
                new BigDecimal("35.101243")
        );

        assertThat(route).isPresent();
        assertThat(route.get().totalSeconds()).isEqualTo(1773);
        assertThat(route.get().distanceMeters()).isEqualTo(2155);
        assertThat(route.get().coordinates()).containsExactly(
                coordinate("129.04025010017244", "35.11511929726355"),
                coordinate("129.0399", "35.1148"),
                coordinate("129.0395", "35.1144")
        );
    }

    @Test
    @DisplayName("API Key가 없으면 HTTP 호출 없이 빈 결과를 반환한다")
    void missingApiKeyReturnsEmptyWithoutHttpCall() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        TmapWalkingClient client = createClient("");

        assertThat(client.findWalkingRoute(
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                new BigDecimal("129.032338"),
                new BigDecimal("35.101243")
        )).isEmpty();
    }

    @Test
    @DisplayName("TMAP HTTP 오류는 빈 결과로 변환한다")
    void httpErrorReturnsEmpty() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/tmap/routes/pedestrian", exchange -> {
            byte[] responseBody = "forbidden".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();

        TmapWalkingClient client = createClient("test-api-key");

        assertThat(client.findWalkingRoute(
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                new BigDecimal("129.032338"),
                new BigDecimal("35.101243")
        )).isEmpty();
    }

    private TmapWalkingClient createClient(String appKey) {
        TmapProperties properties = new TmapProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                appKey,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        TmapConfig config = new TmapConfig();
        return new TmapWalkingClient(config.tmapRestClient(properties), properties);
    }

    private void handlePedestrianRoute(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        assertThat(exchange.getRequestHeaders().getFirst("appKey")).isEqualTo("test-api-key");
        assertThat(exchange.getRequestURI().getQuery()).contains("version=1", "format=json");

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(requestBody).contains(
                "\"startX\":\"129.0403\"",
                "\"startY\":\"35.1151\"",
                "\"endX\":\"129.032338\"",
                "\"endY\":\"35.101243\""
        );

        byte[] responseBody = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "properties": {
                        "totalDistance": 2155,
                        "totalTime": 1773
                      },
                      "geometry": {
                        "type": "Point",
                        "coordinates": [129.04025010017244, 35.11511929726355]
                      }
                    },
                    {
                      "geometry": {
                        "type": "LineString",
                        "coordinates": [
                          [129.04025010017244, 35.11511929726355],
                          [129.03990000000000, 35.11480000000000]
                        ]
                      }
                    },
                    {
                      "geometry": {
                        "type": "LineString",
                        "coordinates": [
                          [129.03990000000000, 35.11480000000000],
                          [129.03950000000000, 35.11440000000000]
                        ]
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private List<BigDecimal> coordinate(String longitude, String latitude) {
        return List.of(new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
