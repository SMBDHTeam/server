package com.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 목록 화면용 축약 응답. 하루 단위 방문지와 경로는 담지 않는다.
 * 상세가 필요하면 {@code GET /api/v1/schedules/{scheduleId}}를 사용한다.
 */
public record ScheduleSummaryResponse(
        @Schema(example = "f2536c52-69d1-4e6c-8ab6-2ede45dba2cd") UUID id,
        @Schema(example = "CONFIRMED") String status,
        @Schema(example = "2026-09-20") LocalDate startDate,
        @Schema(example = "2026-09-21") LocalDate endDate,
        @Schema(example = "친구와 함께하는 여유로운 자연 중심 일정") String styleSummary,
        @Schema(description = "총 일차 수", example = "2") int dayCount,
        @Schema(description = "전체 방문지 수", example = "6") int stopCount,
        @Schema(description = "카드 미리보기용 장소 이름. 방문 순서대로 최대 3개, 중복 제거",
                example = "[\"부산역\", \"감천문화마을\", \"자갈치시장\"]")
        List<String> previewPlaceNames
) {

    /** 목록 카드에 미리보기로 노출할 장소 이름 최대 개수. */
    private static final int PREVIEW_PLACE_LIMIT = 3;

    public static ScheduleSummaryResponse from(ScheduleResponse schedule) {
        List<ScheduleResponse.Day> days = schedule.days() == null ? List.of() : schedule.days();
        List<ScheduleResponse.Stop> stops = days.stream()
                .map(day -> day.stops() == null ? List.<ScheduleResponse.Stop>of() : day.stops())
                .flatMap(List::stream)
                .toList();

        List<String> previewPlaceNames = stops.stream()
                .map(ScheduleResponse.Stop::place)
                .filter(place -> place != null && place.name() != null)
                .map(ScheduleResponse.Place::name)
                .distinct()
                .limit(PREVIEW_PLACE_LIMIT)
                .toList();

        return new ScheduleSummaryResponse(
                schedule.id(),
                schedule.status(),
                schedule.startDate(),
                schedule.endDate(),
                schedule.styleSummary(),
                days.size(),
                stops.size(),
                previewPlaceNames
        );
    }
}
