package com.server.schedule.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ScheduleE2EAssertions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ScheduleE2EAssertions() {
    }

    static void assertSuccessfulCreate(
            String responseBody,
            LocalDate startDate,
            int expectedDayCount,
            List<Long> mustVisitPlaceIds
    ) throws JsonProcessingException {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        JsonNode days = root.path("days");

        assertThat(root.path("evaluation").path("hardGate").path("passed").asBoolean()).isTrue();
        assertThat(root.path("evaluation").path("hardGate").path("violations").isEmpty()).isTrue();
        assertThat(root.path("evaluation").path("qualityScore").path("maxScore").asInt()).isEqualTo(100);
        assertThat(root.path("evaluation").path("operations").path("routeCount").asInt()).isPositive();
        assertThat(days.isArray()).isTrue();
        assertThat(days.size()).isEqualTo(expectedDayCount);

        List<Long> scheduledPlaceIds = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            JsonNode day = days.get(dayIndex);
            JsonNode stops = day.path("stops");
            LocalTime dayStart = LocalTime.parse(day.path("startTime").asText());
            LocalTime dayEnd = LocalTime.parse(day.path("endTime").asText());

            assertThat(day.path("dayNo").asInt()).isEqualTo(dayIndex + 1);
            assertThat(LocalDate.parse(day.path("date").asText())).isEqualTo(startDate.plusDays(dayIndex));
            assertThat(day.path("startLocation").path("name").asText()).isNotBlank();
            assertThat(day.path("endLocation").path("name").asText()).isNotBlank();
            assertThat(stops.isArray()).isTrue();
            assertThat(stops.size()).isPositive();

            LocalTime previousDeparture = dayStart;
            for (int stopIndex = 0; stopIndex < stops.size(); stopIndex++) {
                JsonNode stop = stops.get(stopIndex);
                JsonNode inboundTransit = stop.path("inboundTransit");
                LocalTime arriveAt = LocalTime.parse(stop.path("arriveAt").asText());
                LocalTime departAt = LocalTime.parse(stop.path("departAt").asText());

                assertThat(stop.path("order").asInt()).isEqualTo(stopIndex + 1);
                assertThat(inboundTransit.isMissingNode() || inboundTransit.isNull()).isFalse();
                assertThat(LocalTime.parse(inboundTransit.path("departAt").asText()))
                        .isEqualTo(previousDeparture);
                assertThat(LocalTime.parse(inboundTransit.path("arriveAt").asText())).isEqualTo(arriveAt);
                assertThat(arriveAt).isAfterOrEqualTo(previousDeparture);
                assertThat(departAt).isAfterOrEqualTo(arriveAt).isBeforeOrEqualTo(dayEnd);

                scheduledPlaceIds.add(stop.path("place").path("id").asLong());
                previousDeparture = departAt;
            }

            JsonNode finalTransit = day.path("finalTransit");
            assertThat(finalTransit.isMissingNode() || finalTransit.isNull()).isFalse();
            assertThat(LocalTime.parse(finalTransit.path("departAt").asText())).isEqualTo(previousDeparture);
            assertThat(LocalTime.parse(finalTransit.path("arriveAt").asText())).isBeforeOrEqualTo(dayEnd);
        }

        Set<Long> uniquePlaceIds = new HashSet<>(scheduledPlaceIds);
        assertThat(uniquePlaceIds).hasSameSizeAs(scheduledPlaceIds);
        assertThat(scheduledPlaceIds).containsAll(mustVisitPlaceIds);
        mustVisitPlaceIds.forEach(mustVisitPlaceId -> assertThat(scheduledPlaceIds)
                .filteredOn(mustVisitPlaceId::equals)
                .hasSize(1));
    }
}
