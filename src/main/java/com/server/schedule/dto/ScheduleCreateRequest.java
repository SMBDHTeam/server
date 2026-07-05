package com.server.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ScheduleCreateRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalTime dailyStartTime,
        @NotNull LocalTime dailyEndTime,
        @Valid @NotNull Location startLocation,
        @Valid @NotNull Location endLocation,
        @Valid @NotEmpty List<SelectedAnswer> selectedAnswers,
        List<Long> mustVisitPlaceIds
) {

    public List<Long> mustVisitPlaceIdsOrEmpty() {
        return mustVisitPlaceIds == null ? List.of() : List.copyOf(mustVisitPlaceIds);
    }

    public record Location(
            @NotBlank String name,
            @NotNull BigDecimal longitude,
            @NotNull BigDecimal latitude
    ) {
    }

    public record SelectedAnswer(
            @NotBlank String questionId,
            @NotBlank String answerId
    ) {
    }
}
