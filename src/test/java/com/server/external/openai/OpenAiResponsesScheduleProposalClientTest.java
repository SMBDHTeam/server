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

class OpenAiResponsesScheduleProposalClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void requestsStrictCandidateBoundedOutputAndParsesProposal() throws Exception {
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
                          "text":"{\\\"days\\\":[{\\\"dayNo\\\":1,\\\"placeIds\\\":[\\\"101\\\",\\\"102\\\"]}],\\\"confidence\\\":93,\\\"summary\\\":\\\"해변과 식사를 연결\\\"}"
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
                true, baseUrl, "test-key", "test-model",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        OpenAiResponsesScheduleProposalClient client = new OpenAiResponsesScheduleProposalClient(
                RestClient.builder().baseUrl(baseUrl).build(), properties, new ObjectMapper());

        var result = client.propose(request());

        assertThat(result.days()).containsExactly(
                new AiScheduleProposalClient.DayProposal(1, List.of(101L, 102L)));
        assertThat(result.confidence()).isEqualTo(93);
        assertThat(requestBody.get())
                .contains("\"name\":\"schedule_assignment\"")
                .contains("\"strict\":true")
                .contains("\"enum\":[\"101\",\"102\"]")
                .contains("\"store\":false")
                .contains("PROMPT_LOW_WALKING");
    }

    private AiScheduleProposalClient.Request request() {
        return new AiScheduleProposalClient.Request(
                List.of(new AiScheduleProposalClient.Day(
                        1, "2026-07-20", "11:00", "19:00", "부산역", "부산역",
                        2, 1, List.of(101L))),
                List.of(
                        new AiScheduleProposalClient.Candidate(
                                101L, "광안리", "관광지", "12", "129.1", "35.1", false),
                        new AiScheduleProposalClient.Candidate(
                                102L, "식당", "음식점", "39", "129.2", "35.2", true)
                ),
                List.of(101L),
                List.of(new AiScheduleProposalClient.SelectedAnswer("PROMPT", "PROMPT_LOW_WALKING")),
                "바다가 보이는 곳"
        );
    }
}
