package com.server.spontaneous.dto;

import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SpontaneousCourseResponse(
        String destinationId,
        String name,
        TransportMode transportMode,
        Integer returnTravelMinutes,
        OffsetDateTime estimatedReturnAt,
        OffsetDateTime returnBy,
        List<CourseStop> course,
        UUID previewId,
        String previewToken,
        OffsetDateTime previewExpiresAt,
        Coordinate startLocation,
        OffsetDateTime startAt,
        ScheduleResponse.Transit finalTransit,
        List<ScheduleMapResponse.RouteLine> routeLines
) {
    public SpontaneousCourseResponse(
            String destinationId, String name, TransportMode transportMode,
            Integer returnTravelMinutes, OffsetDateTime estimatedReturnAt,
            OffsetDateTime returnBy, List<CourseStop> course
    ) {
        this(destinationId, name, transportMode, returnTravelMinutes, estimatedReturnAt,
                returnBy, course, null, null, null, null, null, null, List.of());
    }
}
