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
            int evaluationCoveragePercent,
            int unusedMinutes,
            List<LongTransitWarning> longTransitWarnings,
            String routeConfidence,
            List<Metric> metrics
    ) {
        public QualityScore(int totalScore, int maxScore, List<Metric> metrics) {
            this(totalScore, maxScore, 100, 0, List.of(), "UNKNOWN", metrics);
        }
    }

    public record LongTransitWarning(
            int dayNo,
            int routeOrder,
            String originName,
            String destinationName,
            int totalMinutes
    ) {
    }

    public record Metric(
            String id,
            String label,
            int score,
            int maxScore,
            String reason,
            String status
    ) {
        public Metric(String id, String label, int score, int maxScore, String reason) {
            this(id, label, score, maxScore, reason, "EVALUATED");
        }
    }

    public record Operations(
            long generationMillis,
            String planningMode,
            Integer aiPlanConfidence,
            int multiDayPlanCandidateCount,
            int multiDayPlanRerankedCount,
            int routeEstimateResolutionCount,
            int routeEstimateCacheHitCount,
            int providerEstimateCallCount,
            int providerEstimateFailureCount,
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
