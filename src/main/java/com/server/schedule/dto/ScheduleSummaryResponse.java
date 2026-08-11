package com.server.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 목록 화면용 축약 응답. 하루 단위 방문지와 경로는 담지 않는다.
 * 상세가 필요하면 {@code GET /api/v1/schedules/{scheduleId}}를 사용한다.
 */
public record ScheduleSummaryResponse(
        UUID id,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String styleSummary,
        int dayCount,
        int stopCount,
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
