package com.server.schedule.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String styleSummary,
        List<Day> days
) {

    public record Day(
            int dayNo,
            LocalDate date,
            List<Stop> stops,
            Transit finalTransit
    ) {
    }

    public record Stop(
            UUID id,
            int order,
            int stayMinutes,
            Place place,
            Transit inboundTransit
    ) {
    }

    public record Place(
            Long id,
            String name,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
    }

    public record Transit(
            int totalMinutes,
            Integer fareAmount,
            List<Segment> segments
    ) {
    }

    public record Segment(
            String mode,
            String lineName,
            String startStationName,
            String endStationName
    ) {
    }
}
