package com.server.share.dto;

import com.server.schedule.dto.ScheduleResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SharedScheduleResponse(
        UUID id,
        String status,
        boolean readOnly,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime dailyStartTime,
        LocalTime dailyEndTime,
        String styleSummary,
        List<ScheduleResponse.Day> days
) {
    public static SharedScheduleResponse from(ScheduleResponse response) {
        return new SharedScheduleResponse(
                response.id(),
                response.status(),
                true,
                response.startDate(),
                response.endDate(),
                response.dailyStartTime(),
                response.dailyEndTime(),
                response.styleSummary(),
                response.days()
        );
    }
}
