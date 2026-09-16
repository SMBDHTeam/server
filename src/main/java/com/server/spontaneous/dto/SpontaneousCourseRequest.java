package com.server.spontaneous.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SpontaneousCourseRequest(
        @NotBlank String destinationId,
        @Valid @NotNull(message = "출발 위치를 설정해 주세요.") Coordinate startLocation,
        @NotNull(message = "출발 시간을 선택해 주세요.") OffsetDateTime startAt,
        @NotNull(message = "복귀 시간을 선택해 주세요.") OffsetDateTime returnBy,
        @NotNull(message = "이동수단을 선택해 주세요.") TransportMode transportMode,
        @Size(max = 3, message = "테마는 최대 3개까지 선택할 수 있습니다.")
        List<@NotNull(message = "테마를 다시 선택해 주세요.") TravelTheme> desiredThemes
) {

    public SpontaneousCourseRequest {
        desiredThemes = desiredThemes == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(desiredThemes));
    }

    @AssertTrue(message = "복귀 시간은 출발 시간보다 늦어야 합니다.")
    public boolean isValidTimeRange() {
        return startAt == null || returnBy == null || returnBy.isAfter(startAt);
    }
}
