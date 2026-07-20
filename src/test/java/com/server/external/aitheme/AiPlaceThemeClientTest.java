package com.server.external.aitheme;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.place.domain.Place;
import com.server.place.support.TourApiTheme;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiPlaceThemeClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void predictsThemeFromAiServer() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "primary_category":"NATURE",
                      "theme_answer_id":"THEME_NATURE",
                      "decision_source":"cat3_prefix",
                      "secondary_themes":["HEALING"],
                      "semantic_tags":["beach","scenic_view"],
                      "is_meal_place":false,
                      "is_low_mobility_friendly":true,
                      "cluster_key":"nature_beach",
                      "reason":"자연 중심 장소"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        AiPlaceThemeProperties properties = new AiPlaceThemeProperties(
                true, baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2));
        AiPlaceThemeClient client = new AiPlaceThemeClient(
                RestClient.builder().baseUrl(baseUrl).build(), properties);

        Optional<TourApiTheme> result = client.predictPrimaryTheme(place());
        Optional<PlaceThemePredictionClient.PlaceThemeInsight> insight = client.predictInsight(place());

        assertThat(result).contains(TourApiTheme.NATURE);
        assertThat(insight).isPresent();
        assertThat(insight.get().secondaryThemes()).contains(TourApiTheme.HEALING);
        assertThat(insight.get().semanticTags()).contains("beach");
        assertThat(insight.get().lowMobilityFriendly()).isTrue();
        assertThat(requestBody.get())
                .contains("\"title\":\"광안리해수욕장\"")
                .contains("\"contenttypeid\":\"12\"")
                .contains("\"cat3\":\"A01011100\"");
    }

    @Test
    void acceptsModelBasedPrediction() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/predict", exchange -> {
            byte[] response = """
                    {
                      "primary_category":"NATURE",
                      "theme_answer_id":"THEME_NATURE",
                      "decision_source":"model"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        AiPlaceThemeProperties properties = new AiPlaceThemeProperties(
                true, baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2));
        AiPlaceThemeClient client = new AiPlaceThemeClient(
                RestClient.builder().baseUrl(baseUrl).build(), properties);

        Optional<TourApiTheme> result = client.predictPrimaryTheme(place());
        Optional<PlaceThemePredictionClient.PlaceThemeInsight> insight = client.predictInsight(place());

        assertThat(result).contains(TourApiTheme.NATURE);
        assertThat(insight).isPresent();
        assertThat(insight.get().primaryTheme()).isEqualTo(TourApiTheme.NATURE);
    }

    private Place place() {
        return new Place(
                "TOUR_API", "AI-PLACE-1", "12", "광안리해수욕장", "A01011100", "부산 수영구",
                new BigDecimal("129.1186"), new BigDecimal("35.1532"), null);
    }
}
