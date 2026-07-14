package com.server.schedule.config;

import com.server.place.domain.Place;
import com.server.place.domain.PlaceIngestionStatus;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.dto.ScheduleCreateRequest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class LocalScheduleOpenApiExampleConfig {

    @Bean
    OpenApiCustomizer localScheduleCreateExampleCustomizer(PlaceRepository placeRepository) {
        return openApi -> replaceFourDayExample(openApi, fixturePlaces(placeRepository));
    }

    private List<Place> fixturePlaces(PlaceRepository placeRepository) {
        return placeRepository.findAll().stream()
                .filter(place -> place.getIngestionStatus() == PlaceIngestionStatus.SYNCED)
                .filter(place -> place.getId() != null
                        && place.getName() != null
                        && !place.getName().contains("테스트")
                        && place.getLongitude() != null
                        && place.getLatitude() != null)
                .sorted(Comparator.comparing(Place::getId))
                .limit(4)
                .toList();
    }

    private void replaceFourDayExample(OpenAPI openApi, List<Place> places) {
        if (places.size() < 4
                || openApi.getPaths() == null
                || openApi.getPaths().get("/api/v1/schedules") == null
                || openApi.getPaths().get("/api/v1/schedules").getPost() == null
                || openApi.getPaths().get("/api/v1/schedules").getPost().getRequestBody() == null
                || openApi.getPaths().get("/api/v1/schedules").getPost().getRequestBody().getContent() == null) {
            return;
        }
        var content = openApi.getPaths().get("/api/v1/schedules")
                .getPost()
                .getRequestBody()
                .getContent()
                .get("application/json");
        if (content == null || content.getExamples() == null) {
            return;
        }
        Example example = content.getExamples().get("fourDay");
        if (example != null) {
            example.setValue(fourDayRequest(places));
        }
    }

    private ScheduleCreateRequest fourDayRequest(List<Place> places) {
        LocalDate startDate = LocalDate.of(2026, 8, 4);
        List<ScheduleCreateRequest.DayCondition> days = java.util.stream.IntStream.range(0, places.size())
                .mapToObj(index -> dayCondition(index + 1, places.get(index)))
                .toList();
        return new ScheduleCreateRequest(
                startDate,
                startDate.plusDays(3),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                location(places.get(0)),
                location(places.get(3)),
                selectedAnswers(),
                places.stream().map(Place::getId).toList(),
                days
        );
    }

    private ScheduleCreateRequest.DayCondition dayCondition(int dayNo, Place place) {
        ScheduleCreateRequest.Location location = location(place);
        return new ScheduleCreateRequest.DayCondition(
                dayNo,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                location,
                location
        );
    }

    private ScheduleCreateRequest.Location location(Place place) {
        return new ScheduleCreateRequest.Location(
                place.getName(),
                place.getLongitude(),
                place.getLatitude()
        );
    }

    private List<ScheduleCreateRequest.SelectedAnswer> selectedAnswers() {
        return List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS"),
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_BALANCED"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_LOCAL"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_LOW_WALK"),
                new ScheduleCreateRequest.SelectedAnswer("TRANSIT", "TRANSIT_SIMPLE")
        );
    }
}
