package com.server.spontaneous.dto;

import com.server.schedule.dto.ScheduleResponse;
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
        List<TravelTheme> themes,
        ScheduleResponse.Place place,
        ScheduleResponse.Transit inboundTransit
) {
    public CourseStop(
            Integer order, CourseRole role, String name, String contentId,
            String contentTypeId, Double latitude, Double longitude,
            Integer travelMinutesFromPrevious, OffsetDateTime arrivalAt,
            OffsetDateTime departureAt, Integer stayMinutes, List<TravelTheme> themes
    ) {
        this(order, role, name, contentId, contentTypeId, latitude, longitude,
                travelMinutesFromPrevious, arrivalAt, departureAt, stayMinutes, themes,
                null, null);
    }
}
