package com.server.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ScheduleResponse(
        @Schema(example = "f2536c52-69d1-4e6c-8ab6-2ede45dba2cd") UUID id,
        @Schema(example = "CONFIRMED", allowableValues = {"DRAFT", "CONFIRMED"}) String status,
        @Schema(example = "2026-09-20") LocalDate startDate,
        @Schema(example = "2026-09-21") LocalDate endDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalTime dailyStartTime,
        @JsonInclude(JsonInclude.Include.NON_NULL) LocalTime dailyEndTime,
        @Schema(description = "선택한 답변을 요약한 문구", example = "친구와 함께하는 여유로운 자연 중심 일정")
        String styleSummary,
        @Schema(description = "일차별 방문지와 이동. 목록 조회에서는 반환하지 않는다.")
        List<Day> days,
        @JsonInclude(JsonInclude.Include.NON_NULL) ScheduleEvaluationReport evaluation,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID previewId,
        @JsonInclude(JsonInclude.Include.NON_NULL) PlanningAssumptions planningAssumptions
) {
    public ScheduleResponse(
            UUID id,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String styleSummary,
            List<Day> days
    ) {
        this(id, status, startDate, endDate, null, null, styleSummary, days, null, null, null);
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
        this(id, status, startDate, endDate, dailyStartTime, dailyEndTime, styleSummary, days, null, null, null);
    }

    public ScheduleResponse(
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
        this(id, status, startDate, endDate, dailyStartTime, dailyEndTime, styleSummary, days,
                evaluation, null, null);
    }

    public record PlanningAssumptions(
            String timeZone,
            String lodgingMode,
            String routeCoverage,
            List<String> warnings
    ) { }

    public record Day(
            int dayNo,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            DayLocation startLocation,
            DayLocation endLocation,
            String startLocationSource,
            String endLocationSource,
            String summary,
            List<Stop> stops,
            Transit finalTransit
    ) {
        public Day(
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
            this(dayNo, date, startTime, endTime, startLocation, endLocation,
                    null, null, summary, stops, finalTransit);
        }

        public Day(
                int dayNo,
                LocalDate date,
                LocalTime startTime,
                LocalTime endTime,
                String summary,
                List<Stop> stops,
                Transit finalTransit
        ) {
            this(dayNo, date, startTime, endTime, null, null,
                    null, null, summary, stops, finalTransit);
        }

        public Day(
                int dayNo,
                LocalDate date,
                List<Stop> stops,
                Transit finalTransit
        ) {
            this(dayNo, date, null, null, null, null,
                    null, null, null, stops, finalTransit);
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
            String mealTimeSlot,
            int waitingMinutesBefore,
            List<String> selectionReasons,
            List<String> warnings
    ) {
        public Stop(
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
            this(id, order, arriveAt, departAt, stayMinutes, place, inboundTransit,
                    null, 0, selectionReasons, warnings);
        }

        public Stop(
                UUID id,
                int order,
                int stayMinutes,
                Place place,
                Transit inboundTransit
        ) {
            this(id, order, null, null, stayMinutes, place, inboundTransit,
                    null, 0, List.of(), List.of());
        }
    }

    public record Place(
            Long id,
            String name,
            String category,
            String categoryLabel,
            String address,
            BigDecimal longitude,
            BigDecimal latitude,
            String primaryImageUrl,
            OperatingInfo operatingInfo
    ) {
        public Place(
                Long id,
                String name,
                String category,
                String address,
                BigDecimal longitude,
                BigDecimal latitude,
                String primaryImageUrl,
                OperatingInfo operatingInfo
        ) {
            this(id, name, category,
                    com.server.place.support.PlaceCategoryLabelResolver.resolve(category, null),
                    address, longitude, latitude, primaryImageUrl, operatingInfo);
        }

        public Place(
                Long id,
                String name,
                BigDecimal longitude,
                BigDecimal latitude
        ) {
            this(id, name, null, "관광지", null, longitude, latitude, null, null);
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
