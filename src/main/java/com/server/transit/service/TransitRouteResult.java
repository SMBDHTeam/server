package com.server.transit.service;

import java.util.List;

public record TransitRouteResult(
        int totalMinutes,
        Integer fareAmount,
        List<Segment> segments,
        List<RouteLine> routeLines,
        String rawJson
) {

    public TransitRouteResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
        routeLines = routeLines == null ? List.of() : List.copyOf(routeLines);
    }

    public record Segment(
            String mode,
            String lineName,
            String startStationName,
            String endStationName
    ) {
    }

    public record RouteLine(
            String mode,
            String lineName,
            String coordinatesJson
    ) {
    }
}
