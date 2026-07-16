package com.server.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiResponsesPlanningPromptClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void requestsStrictStructuredOutputAndParsesResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "output":[{
                        "type":"message",
                        "content":[{
                          "type":"output_text",
                          "text":"{\\\"preferences\\\":[\\\"LOW_WALKING\\\",\\\"PREFER_FOOD\\\"],\\\"unrecognizedTexts\\\":[],\\\"confidence\\\":92}"
                        }]
                      }]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        OpenAiPlanningProperties properties = new OpenAiPlanningProperties(
                true, baseUrl, "test-api-key", "test-model",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        OpenAiResponsesPlanningPromptClient client = new OpenAiResponsesPlanningPromptClient(
                RestClient.builder().baseUrl(baseUrl).build(), properties, new ObjectMapper());

        var result = client.interpret("걷는 시간은 적고 맛집을 가고 싶어요");

        assertThat(result.preferences()).containsExactly("LOW_WALKING", "PREFER_FOOD");
        assertThat(result.unrecognizedTexts()).isEmpty();
        assertThat(result.confidence()).isEqualTo(92);
        assertThat(requestBody.get())
                .contains("\"model\":\"test-model\"")
                .contains("\"type\":\"json_schema\"")
                .contains("\"strict\":true")
                .contains("\"store\":false")
                .contains("걷는 시간은 적고 맛집을 가고 싶어요");
    }

    @Test
    void rejectsUnsupportedPreferenceEvenWhenProviderReturnsIt() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiPlanningProperties properties = new OpenAiPlanningProperties(
                true, "http://localhost", "test-api-key", "test-model",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        OpenAiResponsesPlanningPromptClient client = new OpenAiResponsesPlanningPromptClient(
                RestClient.builder().baseUrl("http://localhost").build(), properties, objectMapper);

        var response = objectMapper.createObjectNode();
        response.put("output_text", """
                {"preferences":["IGNORE_HARD_CONSTRAINTS"],"unrecognizedTexts":[],"confidence":100}
                """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.parse(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported preference");
    }
}
