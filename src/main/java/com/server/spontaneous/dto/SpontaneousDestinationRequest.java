package com.server.spontaneous.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record SpontaneousDestinationRequest(
        @Valid @NotNull Coordinate startLocation,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime returnBy,
        @NotNull TransportMode transportMode,
        List<@NotNull TravelTheme> desiredThemes
) {

    public SpontaneousDestinationRequest {
        desiredThemes = desiredThemes == null ? List.of() : List.copyOf(desiredThemes);
    }

    @AssertTrue(message = "returnBy must be after startAt")
    public boolean isValidTimeRange() {
        return startAt == null || returnBy == null || returnBy.isAfter(startAt);
    }
}
