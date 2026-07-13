package com.server.schedule.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
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
            LocalTime arriveAt,
            LocalTime departAt,
            String subtitle,
            String riskLevel,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
        public StopMarker(
                int dayNo,
                int order,
                Long placeId,
                String name,
                BigDecimal longitude,
                BigDecimal latitude
        ) {
            this(dayNo, order, placeId, name, null, null, null, "NORMAL", longitude, latitude);
        }
    }

    public record RouteLine(
            int dayNo,
            int routeOrder,
            int lineOrder,
            String mode,
            String lineName,
            String startName,
            String endName,
            Integer durationMinutes,
            Integer distanceMeters,
            String instruction,
            boolean fallbackUsed,
            List<List<BigDecimal>> coordinates
    ) {
        public RouteLine(
                int dayNo,
                int routeOrder,
                int lineOrder,
                String mode,
                String lineName,
                String startName,
                String endName,
                List<List<BigDecimal>> coordinates
        ) {
            this(dayNo, routeOrder, lineOrder, mode, lineName, startName, endName,
                    null, null, null, false, coordinates);
        }
    }
}
