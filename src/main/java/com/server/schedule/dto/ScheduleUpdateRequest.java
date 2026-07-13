package com.server.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record ScheduleUpdateRequest(
        @NotEmpty List<@Valid Stop> stops
) {
    public record Stop(
            UUID stopId,
            Long placeId,
            @Positive int dayNo,
            @Positive int order,
            @Min(30) int stayMinutes
    ) {
        @AssertTrue(message = "stopId와 placeId 중 하나만 전달해야 합니다.")
        public boolean isReferenceValid() {
            return (stopId == null) != (placeId == null) && (placeId == null || placeId > 0);
        }
    }
}
