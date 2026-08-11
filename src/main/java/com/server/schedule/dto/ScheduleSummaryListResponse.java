package com.server.schedule.dto;

import java.util.List;

public record ScheduleSummaryListResponse(List<ScheduleSummaryResponse> items) {

    public static ScheduleSummaryListResponse from(ScheduleListResponse schedules) {
        List<ScheduleResponse> items = schedules == null || schedules.items() == null
                ? List.of()
                : schedules.items();
        return new ScheduleSummaryListResponse(
                items.stream().map(ScheduleSummaryResponse::from).toList());
    }
}
