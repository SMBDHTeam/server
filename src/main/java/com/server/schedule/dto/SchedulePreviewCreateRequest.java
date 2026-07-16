package com.server.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SchedulePreviewCreateRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Valid @NotNull Location startLocation,
        LocalTime startTime,
        @Valid LodgingPlan lodgingPlan,
        @Valid EndConstraint endConstraint,
        @Valid @NotEmpty List<SelectedAnswer> selectedAnswers,
        List<Long> mustVisitPlaceIds,
        @Valid List<FixedEvent> fixedEvents,
        @Valid List<DayOverride> dayOverrides,
        @Size(max = 500) String customPrompt,
        String timeZone
) {
    public List<Long> mustVisitPlaceIdsOrEmpty() {
        return mustVisitPlaceIds == null ? List.of() : List.copyOf(mustVisitPlaceIds);
    }

    public List<FixedEvent> fixedEventsOrEmpty() {
        return fixedEvents == null ? List.of() : List.copyOf(fixedEvents);
    }

    public List<DayOverride> dayOverridesOrEmpty() {
        return dayOverrides == null ? List.of() : List.copyOf(dayOverrides);
    }

    public record Location(
            @NotBlank String name,
            String address,
            @NotNull BigDecimal longitude,
            @NotNull BigDecimal latitude
    ) {
    }

    public record LodgingPlan(
            @NotBlank String mode,
            @Valid Location baseLocation,
            @Valid List<NightStay> nightStays
    ) {
        public List<NightStay> nightStaysOrEmpty() {
            return nightStays == null ? List.of() : List.copyOf(nightStays);
        }
    }

    public record NightStay(
            @NotNull LocalDate date,
            @Valid @NotNull Location location
    ) {
    }

    public record EndConstraint(
            @NotBlank String type,
            @Valid @NotNull Location location,
            @NotBlank String targetAt,
            @PositiveOrZero Integer bufferMinutes
    ) {
    }

    public record SelectedAnswer(
            @NotBlank String questionId,
            @NotEmpty List<@NotBlank String> answerIds
    ) {
    }

    public record FixedEvent(
            @NotBlank String clientEventId,
            @NotBlank String name,
            @NotNull Long placeId,
            @NotBlank String startsAt,
            @NotBlank String endsAt
    ) {
    }

    public record DayOverride(
            @NotNull LocalDate date,
            LocalTime availableFrom,
            LocalTime availableUntil,
            @Valid Location startLocation,
            @Valid Location endLocation
    ) {
    }
}
