package com.server.spontaneous.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CourseStop(
        Integer order,
        CourseRole role,
        String name,
        String contentId,
        String contentTypeId,
        Double latitude,
        Double longitude,
        Integer travelMinutesFromPrevious,
        OffsetDateTime arrivalAt,
        OffsetDateTime departureAt,
        Integer stayMinutes,
        List<TravelTheme> themes
) {
}
