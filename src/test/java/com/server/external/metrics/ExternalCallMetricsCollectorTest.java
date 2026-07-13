package com.server.external.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExternalCallMetricsCollectorTest {

    private final ExternalCallMetricsCollector collector = new ExternalCallMetricsCollector();

    @Test
    void collectsCallsInsideScopeAndClearsThemAfterClose() {
        try (ExternalCallMetricsCollector.Scope scope = collector.start()) {
            collector.recordOdsayPathSearch();
            collector.recordOdsayLoadLane();
            collector.recordTmapWalking();
            collector.recordFailure();

            ExternalCallMetricsCollector.Snapshot snapshot = scope.snapshot();
            assertThat(snapshot.totalHttpCallCount()).isEqualTo(3);
            assertThat(snapshot.failureCount()).isEqualTo(1);
        }

        try (ExternalCallMetricsCollector.Scope scope = collector.start()) {
            assertThat(scope.snapshot().totalHttpCallCount()).isZero();
        }
    }
}
