package com.server.schedule.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime dailyStartTime,
        LocalTime dailyEndTime,
        String styleSummary,
        List<Day> days,
        ScheduleEvaluationReport evaluation
) {
    public ScheduleResponse(
            UUID id,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String styleSummary,
            List<Day> days
    ) {
        this(id, status, startDate, endDate, null, null, styleSummary, days, null);
    }

    public ScheduleResponse(
            UUID id,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            String styleSummary,
            List<Day> days
    ) {
        this(id, status, startDate, endDate, dailyStartTime, dailyEndTime, styleSummary, days, null);
    }

    public record Day(
            int dayNo,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            DayLocation startLocation,
            DayLocation endLocation,
            String summary,
            List<Stop> stops,
            Transit finalTransit
    ) {
        public Day(
                int dayNo,
                LocalDate date,
                LocalTime startTime,
                LocalTime endTime,
                String summary,
                List<Stop> stops,
                Transit finalTransit
        ) {
            this(dayNo, date, startTime, endTime, null, null, summary, stops, finalTransit);
        }

        public Day(
                int dayNo,
                LocalDate date,
                List<Stop> stops,
                Transit finalTransit
        ) {
            this(dayNo, date, null, null, null, null, null, stops, finalTransit);
        }
    }

    public record DayLocation(
            String name,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
    }

    public record Stop(
            UUID id,
            int order,
            LocalTime arriveAt,
            LocalTime departAt,
            int stayMinutes,
            Place place,
            Transit inboundTransit,
            List<String> selectionReasons,
            List<String> warnings
    ) {
        public Stop(
                UUID id,
                int order,
                int stayMinutes,
                Place place,
                Transit inboundTransit
        ) {
            this(id, order, null, null, stayMinutes, place, inboundTransit, List.of(), List.of());
        }
    }

    public record Place(
            Long id,
            String name,
            String category,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String primaryImageUrl,
            OperatingInfo operatingInfo
    ) {
        public Place(
                Long id,
                String name,
                BigDecimal longitude,
                BigDecimal latitude
        ) {
            this(id, name, null, null, longitude, latitude, null, null);
        }
    }

    public record OperatingInfo(
            String openingHoursText,
            String closedDaysText,
            boolean requiresManualCheck
    ) {
    }

    public record Transit(
            String routeType,
            int routeOrder,
            String originName,
            String destinationName,
            String summary,
            LocalTime departAt,
            LocalTime arriveAt,
            int totalMinutes,
            int walkMinutes,
            int waitMinutes,
            int transferCount,
            Integer fareAmount,
            String provider,
            String realtimeStatus,
            boolean fallbackUsed,
            List<Segment> segments,
            List<String> warnings
    ) {
        public Transit(
                int totalMinutes,
                Integer fareAmount,
                List<Segment> segments
        ) {
            this(null, 0, null, null, null, null, null, totalMinutes, 0, 0, 0,
                    fareAmount, "UNKNOWN", "UNAVAILABLE", false, segments, List.of());
        }
    }

    public record Segment(
            int order,
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
            this(0, mode, lineName, null, startStationName, null, endStationName,
                    null, 0, null, null, 0, "UNAVAILABLE");
        }
    }
}
