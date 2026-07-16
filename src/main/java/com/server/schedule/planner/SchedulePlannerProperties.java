package com.server.schedule.planner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.schedule-planner")
public record SchedulePlannerProperties(
        boolean actualRouteRerankEnabled,
        int actualRouteRerankCandidates,
        int multiDayActualRerankCandidates,
        int maxRouteEstimateProviderCalls
) {

    public SchedulePlannerProperties {
        actualRouteRerankCandidates = Math.max(1, Math.min(actualRouteRerankCandidates, 6));
        multiDayActualRerankCandidates = Math.max(1, Math.min(multiDayActualRerankCandidates, 6));
        maxRouteEstimateProviderCalls = Math.max(0, Math.min(maxRouteEstimateProviderCalls, 100));
    }

    public static SchedulePlannerProperties defaults() {
        return new SchedulePlannerProperties(true, 3, 3, 30);
    }
}
