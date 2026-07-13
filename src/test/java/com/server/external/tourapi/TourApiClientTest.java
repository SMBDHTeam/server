package com.server.external.tourapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TourAPI 클라이언트")
class TourApiClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("장소 목록 응답을 내부 DTO로 변환한다")
    void searchPlacesConvertsTourApiResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/areaBasedList2", this::handleAreaBasedList);
        server.start();

        TourApiClient client = createClient("test-api-key");

        TourApiPlaceListResponse response = client.searchPlaces("6", "12", 1, 100);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        TourApiPlaceListResponse.Item item = response.items().get(0);
        assertThat(item.contentId()).isEqualTo("126508");
        assertThat(item.contentTypeId()).isEqualTo("12");
        assertThat(item.title()).isEqualTo("이송도전망대");
        assertThat(item.category()).isEqualTo("A01010100");
        assertThat(item.address()).isEqualTo("부산 서구 암남동 620-53");
        assertThat(item.longitude()).isEqualTo("129.047956");
        assertThat(item.latitude()).isEqualTo("35.075519");
        assertThat(item.firstImage()).isEqualTo("https://example.com/image.jpg");
        assertThat(item.modifiedTime()).isEqualTo("20260713040000");
        assertThat(item.rawJson()).contains("\"contentid\":\"126508\"");
    }

    @Test
    @DisplayName("상세·소개·이미지 응답을 내부 DTO로 변환한다")
    void detailEndpointsConvertTourApiResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/detailCommon2", this::handleDetailCommon);
        server.createContext("/detailIntro2", this::handleDetailIntro);
        server.createContext("/detailImage2", this::handleDetailImage);
        server.start();

        TourApiClient client = createClient("test-api-key");

        TourApiPlaceDetailResponse detail = client.getCommonDetail("126508");
        TourApiPlaceIntroResponse intro = client.getIntro("126508", "12");
        TourApiPlaceImageResponse images = client.getImages("126508");

        assertThat(detail.overview()).isEqualTo("장소 설명");
        assertThat(detail.homepage()).isEqualTo("https://example.com");
        assertThat(intro.openingHoursText()).isEqualTo("09:00~18:00");
        assertThat(intro.closedDaysText()).isEqualTo("연중무휴");
        assertThat(intro.parkingText()).isEqualTo("주차 가능");
        assertThat(intro.requiresManualCheck()).isTrue();
        assertThat(images.items()).hasSize(1);
        assertThat(images.items().get(0).url()).isEqualTo("https://example.com/image.jpg");
        assertThat(images.items().get(0).thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(images.items().get(0).copyrightType()).isEqualTo("Type1");
    }

    @Test
    @DisplayName("API Key가 없으면 HTTP 호출 없이 Provider 장애로 변환한다")
    void missingApiKeyThrowsProviderUnavailableWithoutHttpCall() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        TourApiClient client = createClient("");

        assertThatThrownBy(() -> client.searchPlaces("6", "12", 1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("TourAPI 실패 코드는 Provider 장애로 변환한다")
    void tourApiFailureResultCodeThrowsProviderUnavailable() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/areaBasedList2", exchange -> respond(exchange, """
                {
                  "response": {
                    "header": {
                      "resultCode": "99",
                      "resultMsg": "SERVICE ERROR"
                    },
                    "body": {
                      "items": {}
                    }
                  }
                }
                """));
        server.start();

        TourApiClient client = createClient("test-api-key");

        assertThatThrownBy(() -> client.searchPlaces("6", "12", 1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("TourAPI 최상위 실패 코드는 Provider 장애로 변환한다")
    void tourApiTopLevelFailureResultCodeThrowsProviderUnavailable() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/detailCommon2", exchange -> respond(exchange, """
                {
                  "responseTime": "2026-07-13T21:33:17.391",
                  "resultCode": "10",
                  "resultMsg": "INVALID_REQUEST_PARAMETER_ERROR"
                }
                """));
        server.start();

        TourApiClient client = createClient("test-api-key");

        assertThatThrownBy(() -> client.getCommonDetail("126508"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("TourAPI HTTP 오류는 Provider 장애로 변환한다")
    void tourApiHttpErrorThrowsProviderUnavailable() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/areaBasedList2", exchange -> {
            byte[] responseBody = "server error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();

        TourApiClient client = createClient("test-api-key");

        assertThatThrownBy(() -> client.searchPlaces("6", "12", 1, 100))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    private TourApiClient createClient(String apiKey) {
        TourApiProperties properties = new TourApiProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                apiKey,
                "ETC",
                "tour-server-test",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        TourApiConfig config = new TourApiConfig();
        return new TourApiClient(config.tourApiRestClient(properties), properties);
    }

    private void handleAreaBasedList(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestURI().getQuery())
                .contains("serviceKey=test-api-key", "areaCode=6", "contentTypeId=12", "pageNo=1", "numOfRows=100");
        respond(exchange, """
                {
                  "response": {
                    "header": { "resultCode": "0000" },
                    "body": {
                      "totalCount": 1,
                      "items": {
                        "item": [
                          {
                            "contentid": "126508",
                            "contenttypeid": "12",
                            "title": "이송도전망대",
                            "cat1": "A01",
                            "cat2": "A0101",
                            "cat3": "A01010100",
                            "addr1": "부산 서구 암남동",
                            "addr2": "620-53",
                            "mapx": "129.047956",
                            "mapy": "35.075519",
                            "firstimage": "https://example.com/image.jpg",
                            "modifiedtime": "20260713040000"
                          }
                        ]
                      }
                    }
                  }
                }
                """);
    }

    private void handleDetailCommon(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestURI().getQuery())
                .contains(
                        "serviceKey=test-api-key",
                        "MobileOS=ETC",
                        "MobileApp=tour-server-test",
                        "_type=json",
                        "contentId=126508"
                )
                .doesNotContain(
                        "contentTypeId=",
                        "defaultYN=",
                        "firstImageYN=",
                        "addrinfoYN=",
                        "mapinfoYN=",
                        "overviewYN="
                );
        respond(exchange, """
                {
                  "response": {
                    "header": { "resultCode": "0000" },
                    "body": {
                      "items": {
                        "item": {
                          "overview": "장소 설명",
                          "homepage": "https://example.com"
                        }
                      }
                    }
                  }
                }
                """);
    }

    private void handleDetailIntro(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestURI().getQuery()).contains("contentId=126508", "contentTypeId=12");
        respond(exchange, """
                {
                  "response": {
                    "header": { "resultCode": "0000" },
                    "body": {
                      "items": {
                        "item": {
                          "usetime": "09:00~18:00",
                          "restdate": "연중무휴",
                          "parking": "주차 가능"
                        }
                      }
                    }
                  }
                }
                """);
    }

    private void handleDetailImage(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestURI().getQuery())
                .contains("contentId=126508", "imageYN=Y")
                .doesNotContain("subImageYN=");
        respond(exchange, """
                {
                  "response": {
                    "header": { "resultCode": "0000" },
                    "body": {
                      "items": {
                        "item": [
                          {
                            "originimgurl": "https://example.com/image.jpg",
                            "smallimageurl": "https://example.com/thumb.jpg",
                            "cpyrhtDivCd": "Type1"
                          }
                        ]
                      }
                    }
                  }
                }
                """);
    }

    private void respond(HttpExchange exchange, String response) throws IOException {
        byte[] responseBody = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }
}
