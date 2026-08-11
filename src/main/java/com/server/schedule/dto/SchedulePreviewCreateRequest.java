package com.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(description = "여행 시작일", example = "2026-09-20")
        @NotNull(message = "출발일은 필수입니다.") LocalDate startDate,
        @Schema(description = "여행 종료일. 시작일 포함 최대 4일", example = "2026-09-21")
        @NotNull(message = "도착일은 필수입니다.") LocalDate endDate,
        @Valid @NotNull(message = "출발 장소는 필수입니다.") Location startLocation,
        @Schema(description = "첫날 출발 시각. 생략하면 기본값을 적용하고 응답의 appliedDefaults로 알린다.",
                example = "10:00")
        LocalTime startTime,
        @Valid @NotNull LodgingPlan lodgingPlan,
        @Valid EndConstraint endConstraint,
        @Valid @NotEmpty List<SelectedAnswer> selectedAnswers,
        @Schema(description = "반드시 방문할 장소의 내부 ID. 외부 검색 결과는 먼저 "
                + "POST /api/v1/places/resolve 로 내부 ID를 받아야 한다.",
                example = "[]")
        List<Long> mustVisitPlaceIds,
        @Valid List<FixedEvent> fixedEvents,
        @Valid List<DayOverride> dayOverrides,
        @Schema(description = "자유 요청. 최대 500자", example = "바다를 많이 보고 걷는 구간은 적었으면 좋겠어요")
        @Size(max = 500) String customPrompt,
        @Schema(description = "생략하면 Asia/Seoul", example = "Asia/Seoul")
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
            @Schema(description = "장소명", example = "부산역")
            @NotBlank String name,
            @Schema(description = "주소", example = "부산 동구 중앙대로 206")
            String address,
            @Schema(description = "경도. WGS84", example = "129.0403")
            @NotNull BigDecimal longitude,
            @Schema(description = "위도. WGS84", example = "35.1151")
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
