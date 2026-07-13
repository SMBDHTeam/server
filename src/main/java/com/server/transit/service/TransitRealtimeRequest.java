package com.server.transit.service;

public record TransitRealtimeRequest(
        String mode,
        String lineName,
        String startStationName,
        String endStationName,
        String startStationId,
        String endStationId
) {
}
