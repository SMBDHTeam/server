package com.server.transit.service;

import java.util.Objects;

public record TransitRouteEstimate(
        TransitRouteResult route,
        DetailLevel detailLevel,
        DetailContext detailContext
) {

    public TransitRouteEstimate {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(detailLevel, "detailLevel");
    }

    public static TransitRouteEstimate estimated(TransitRouteResult route, DetailContext detailContext) {
        return new TransitRouteEstimate(route, DetailLevel.ESTIMATE, detailContext);
    }

    public static TransitRouteEstimate detailed(TransitRouteResult route) {
        return new TransitRouteEstimate(route, DetailLevel.DETAILED, null);
    }

    public boolean requiresDetail() {
        return detailLevel == DetailLevel.ESTIMATE;
    }

    public enum DetailLevel {
        ESTIMATE,
        DETAILED
    }

    public interface DetailContext {
    }
}
