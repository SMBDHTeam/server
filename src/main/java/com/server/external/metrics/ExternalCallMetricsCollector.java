package com.server.external.metrics;

import org.springframework.stereotype.Component;

@Component
public class ExternalCallMetricsCollector {

    private final ThreadLocal<MutableMetrics> current = new ThreadLocal<>();

    public Scope start() {
        if (current.get() != null) {
            throw new IllegalStateException("External call metrics scope is already active");
        }
        MutableMetrics metrics = new MutableMetrics();
        current.set(metrics);
        return new Scope(this, metrics);
    }

    public void recordOdsayPathSearch() {
        MutableMetrics metrics = current.get();
        if (metrics != null) metrics.odsayPathSearchCount++;
    }

    public void recordOdsayLoadLane() {
        MutableMetrics metrics = current.get();
        if (metrics != null) metrics.odsayLoadLaneCount++;
    }

    public void recordTmapWalking() {
        MutableMetrics metrics = current.get();
        if (metrics != null) metrics.tmapWalkingCount++;
    }

    public void recordFailure() {
        MutableMetrics metrics = current.get();
        if (metrics != null) metrics.failureCount++;
    }

    private void close(MutableMetrics metrics) {
        if (current.get() == metrics) current.remove();
    }

    public record Snapshot(
            int odsayPathSearchCount,
            int odsayLoadLaneCount,
            int tmapWalkingCount,
            int failureCount
    ) {
        public int totalHttpCallCount() {
            return odsayPathSearchCount + odsayLoadLaneCount + tmapWalkingCount;
        }
    }

    public static final class Scope implements AutoCloseable {

        private final ExternalCallMetricsCollector collector;
        private final MutableMetrics metrics;
        private boolean closed;

        private Scope(ExternalCallMetricsCollector collector, MutableMetrics metrics) {
            this.collector = collector;
            this.metrics = metrics;
        }

        public Snapshot snapshot() {
            return new Snapshot(
                    metrics.odsayPathSearchCount,
                    metrics.odsayLoadLaneCount,
                    metrics.tmapWalkingCount,
                    metrics.failureCount
            );
        }

        @Override
        public void close() {
            if (!closed) {
                collector.close(metrics);
                closed = true;
            }
        }
    }

    private static final class MutableMetrics {

        private int odsayPathSearchCount;
        private int odsayLoadLaneCount;
        private int tmapWalkingCount;
        private int failureCount;
    }
}
