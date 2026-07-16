package com.server.schedule.evaluation;

import java.util.List;

public record ScheduleScoreResult(
        int totalScore,
        List<Metric> metrics
) {

    public record Metric(
            String id,
            String label,
            int maxScore,
            int score,
            String reason,
            String status
    ) {
        public Metric(String id, String label, int maxScore, int score, String reason) {
            this(id, label, maxScore, score, reason, "EVALUATED");
        }
    }
}
