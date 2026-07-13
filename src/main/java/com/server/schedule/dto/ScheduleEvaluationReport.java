package com.server.schedule.dto;

import java.util.List;

public record ScheduleEvaluationReport(
        HardGate hardGate,
        QualityScore qualityScore,
        Operations operations
) {

    public record HardGate(
            boolean passed,
            List<String> violations
    ) {
    }

    public record QualityScore(
            int totalScore,
            int maxScore,
            List<Metric> metrics
    ) {
    }

    public record Metric(
            String id,
            String label,
            int score,
            int maxScore,
            String reason
    ) {
    }

    public record Operations(
            long generationMillis,
            int routeResolutionCount,
            int routeCacheHitCount,
            int providerCallCount,
            int providerFailureCount,
            int externalHttpCallCount,
            int externalHttpFailureCount,
            int odsayPathSearchCount,
            int odsayLoadLaneCount,
            int tmapWalkingCount,
            int routeCount,
            int fallbackRouteCount,
            int geometryFallbackLineCount,
            int totalTransitMinutes,
            int totalWalkMinutes,
            int totalTransferCount,
            List<String> providers
    ) {
    }
}
