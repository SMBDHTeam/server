package com.server.transit.service;

import java.util.List;

public record TransitRouteResult(
        int totalMinutes,
        Integer fareAmount,
        String provider,
        String realtimeStatus,
        boolean fallbackUsed,
        List<String> warnings,
        List<Segment> segments,
        List<RouteLine> routeLines,
        String rawJson
) {

    public TransitRouteResult(
            int totalMinutes,
            Integer fareAmount,
            List<Segment> segments,
            List<RouteLine> routeLines,
            String rawJson
    ) {
        this(totalMinutes, fareAmount, "UNKNOWN", "UNAVAILABLE", false, List.of(), segments, routeLines, rawJson);
    }

    public TransitRouteResult {
        provider = provider == null || provider.isBlank() ? "UNKNOWN" : provider;
        realtimeStatus = realtimeStatus == null || realtimeStatus.isBlank() ? "UNAVAILABLE" : realtimeStatus;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        segments = segments == null ? List.of() : List.copyOf(segments);
        routeLines = routeLines == null ? List.of() : List.copyOf(routeLines);
    }

    public record Segment(
            String mode,
            String lineName,
            String startStationId,
            String startStationName,
            String endStationId,
            String endStationName,
            String instruction,
            int durationMinutes,
            Integer distanceMeters,
            Integer stationCount,
            int waitMinutes,
            String realtimeStatus
    ) {
        public Segment(
                String mode,
                String lineName,
                String startStationName,
                String endStationName
        ) {
            this(
                    mode,
                    lineName,
                    null,
                    startStationName,
                    null,
                    endStationName,
                    null,
                    0,
                    null,
                    null,
                    0,
                    "UNAVAILABLE"
            );
        }

        public Segment {
            realtimeStatus = realtimeStatus == null || realtimeStatus.isBlank() ? "UNAVAILABLE" : realtimeStatus;
        }
    }

    public record RouteLine(
            String mode,
            String lineName,
            String coordinatesJson,
            Integer durationMinutes,
            Integer distanceMeters,
            String instruction,
            boolean fallbackUsed
    ) {
        public RouteLine(
                String mode,
                String lineName,
                String coordinatesJson
        ) {
            this(mode, lineName, coordinatesJson, null, null, null, false);
        }
    }
}
