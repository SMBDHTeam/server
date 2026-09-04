package com.server.external.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Kakao Local 클라이언트")
class KakaoLocalClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("키워드 검색 응답을 Kakao Local DTO로 변환한다")
    void searchKeywordConvertsKakaoLocalResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/local/search/keyword.json", this::handleKeywordSearch);
        server.start();

        KakaoLocalClient client = createClient("test-api-key");

        KakaoLocalSearchResponse response = client.searchKeyword("부산역", 10);

        assertThat(response.documentsOrEmpty()).hasSize(1);
        KakaoLocalSearchResponse.Document document = response.documentsOrEmpty().get(0);
        assertThat(document.id()).isEqualTo("kakao-place-id");
        assertThat(document.placeName()).isEqualTo("부산역");
        assertThat(document.addressName()).isEqualTo("부산 동구 중앙대로 206");
        assertThat(document.x()).isEqualTo("129.0403");
        assertThat(document.y()).isEqualTo("35.1151");
    }

    @Test
    @DisplayName("좌표를 행정구역 응답 DTO로 변환한다")
    void searchRegionCodeConvertsKakaoLocalResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/local/geo/coord2regioncode.json", this::handleRegionCodeSearch);
        server.start();

        KakaoLocalClient client = createClient("test-api-key");

        KakaoLocalRegionCodeResponse response = client.searchRegionCode(
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151")
        );

        assertThat(response.documentsOrEmpty()).hasSize(1);
        KakaoLocalRegionCodeResponse.Document document = response.documentsOrEmpty().get(0);
        assertThat(document.regionType()).isEqualTo("H");
        assertThat(document.region1DepthName()).isEqualTo("부산광역시");
        assertThat(document.region2DepthName()).isEqualTo("동구");
        assertThat(document.x()).isEqualTo(129.0403);
        assertThat(document.y()).isEqualTo(35.1151);
    }

    @Test
    @DisplayName("API Key가 없으면 HTTP 호출 없이 Provider 장애로 변환한다")
    void missingApiKeyThrowsProviderUnavailableWithoutHttpCall() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        KakaoLocalClient client = createClient("");

        assertThatThrownBy(() -> client.searchKeyword("부산역", 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    private KakaoLocalClient createClient(String apiKey) {
        KakaoLocalProperties properties = new KakaoLocalProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                apiKey,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        KakaoLocalConfig config = new KakaoLocalConfig();
        return new KakaoLocalClient(config.kakaoLocalRestClient(properties), properties);
    }

    private void handleKeywordSearch(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("KakaoAK test-api-key");
        assertThat(exchange.getRequestURI().getQuery()).contains("query=", "size=10");

        byte[] responseBody = """
                {
                  "documents": [
                    {
                      "id": "kakao-place-id",
                      "place_name": "부산역",
                      "address_name": "부산 동구 중앙대로 206",
                      "x": "129.0403",
                      "y": "35.1151",
                      "distance": "",
                      "place_url": "https://place.map.kakao.com/1"
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private void handleRegionCodeSearch(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("KakaoAK test-api-key");
        assertThat(exchange.getRequestURI().getQuery()).contains("x=129.0403", "y=35.1151");

        byte[] responseBody = """
                {
                  "documents": [
                    {
                      "region_type": "H",
                      "address_name": "부산광역시 동구 초량제3동",
                      "region_1depth_name": "부산광역시",
                      "region_2depth_name": "동구",
                      "region_3depth_name": "초량제3동",
                      "region_4depth_name": "",
                      "code": "2611056000",
                      "x": 129.0403,
                      "y": 35.1151
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }
}
