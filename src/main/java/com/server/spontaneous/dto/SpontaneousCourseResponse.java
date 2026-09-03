package com.server.spontaneous.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SpontaneousCourseResponse(
        String destinationId,
        String name,
        TransportMode transportMode,
        Integer returnTravelMinutes,
        OffsetDateTime estimatedReturnAt,
        OffsetDateTime returnBy,
        List<CourseStop> course
) {
}
