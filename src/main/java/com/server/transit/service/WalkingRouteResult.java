package com.server.transit.service;

public record WalkingRouteResult(
        int totalMinutes,
        Integer distanceMeters,
        String coordinatesJson
) {
}
