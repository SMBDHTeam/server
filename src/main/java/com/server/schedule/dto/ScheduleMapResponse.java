package com.server.schedule.dto;

import java.math.BigDecimal;
import java.util.List;

public record ScheduleMapResponse(
        Marker startMarker,
        Marker endMarker,
        List<StopMarker> markers,
        List<RouteLine> routeLines
) {

    public record Marker(
            String name,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
    }

    public record StopMarker(
            int dayNo,
            int order,
            Long placeId,
            String name,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
    }

    public record RouteLine(
            int dayNo,
            int routeOrder,
            int lineOrder,
            String mode,
            String lineName,
            String startName,
            String endName,
            List<List<BigDecimal>> coordinates
    ) {
    }
}
