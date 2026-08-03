package com.server.external.odsay;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.external.metrics.ExternalCallMetricsCollector;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OdsayClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void spacesRequestsByConfiguredMinimumInterval() throws Exception {
        List<Long> receivedAtNanos = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/api/searchPubTransPathT", exchange -> {
            receivedAtNanos.add(System.nanoTime());
            byte[] body = "{\"result\":{\"path\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OdsayProperties properties = new OdsayProperties(
                true,
                "http://localhost:" + server.getAddress().getPort(),
                "test-key",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofMillis(50)
        );
        OdsayClient client = new OdsayClient(
                RestClient.builder().build(),
                properties,
                new ExternalCallMetricsCollector()
        );

        client.searchPublicTransitPath(decimal("129.04"), decimal("35.11"), decimal("129.05"), decimal("35.12"));
        client.searchPublicTransitPath(decimal("129.05"), decimal("35.12"), decimal("129.06"), decimal("35.13"));

        assertThat(receivedAtNanos).hasSize(2);
        assertThat(Duration.ofNanos(receivedAtNanos.get(1) - receivedAtNanos.get(0)))
                .isGreaterThanOrEqualTo(Duration.ofMillis(40));
    }

    @Test
    void allowsRequestsToOverlapWhileStillSpacingTheirDispatch() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peakInFlight = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/v1/api/searchPubTransPathT", exchange -> {
            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            byte[] body = "{\"result\":{\"path\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        OdsayProperties properties = new OdsayProperties(
                true,
                "http://localhost:" + server.getAddress().getPort(),
                "test-key",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofMillis(20)
        );
        OdsayClient client = new OdsayClient(
                RestClient.builder().build(),
                properties,
                new ExternalCallMetricsCollector()
        );

        ExecutorService callers = Executors.newFixedThreadPool(3);
        try {
            List<Future<?>> calls = new java.util.ArrayList<>();
            for (int index = 0; index < 3; index++) {
                calls.add(callers.submit(() -> client.searchPublicTransitPath(
                        decimal("129.04"), decimal("35.11"), decimal("129.05"), decimal("35.12"))));
            }
            for (Future<?> call : calls) {
                call.get(10, TimeUnit.SECONDS);
            }
        } finally {
            callers.shutdownNow();
        }

        assertThat(peakInFlight.get())
                .as("ODsay calls must not be serialized process-wide")
                .isGreaterThan(1);
    }

    private java.math.BigDecimal decimal(String value) {
        return new java.math.BigDecimal(value);
    }
}
