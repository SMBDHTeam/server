package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlaceCandidateProviderTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceCandidateProvider provider = new PlaceCandidateProvider(
            placeRepository, new PlacePreferenceScorer());

    @Test
    void preservesDifferentExperiencesWhenOneThemeHasManyMatchingPlaces() {
        when(placeRepository.findAll()).thenReturn(List.of(
                place(1L, "감천문화마을", "12", "129.0000", "35.0900"),
                place(2L, "흰여울문화마을", "12", "129.0200", "35.1100"),
                place(3L, "색채마을 골목", "12", "129.0400", "35.1400"),
                place(4L, "광복로 패션거리", "12", "129.0600", "35.1700"),
                place(5L, "산복도로 마을", "12", "129.0800", "35.2000"),
                place(6L, "예술마을 거리", "12", "129.1000", "35.2300"),
                place(7L, "해안 마을길", "12", "129.1200", "35.2600"),
                place(8L, "원도심 골목", "12", "129.1400", "35.2900"),
                place(9L, "부산시민공원", "12", "129.0100", "35.1700"),
                place(10L, "부산박물관", "14", "129.0700", "35.1000"),
                place(11L, "송정해수욕장", "12", "129.1500", "35.2000"),
                place(12L, "송도 케이블카", "28", "129.1900", "35.1600")
        ));

        PlaceCandidateProvider.ResolvedPlaces result = provider.resolve(
                request(), List.of(3), List.of(day()));

        long experienceTypeCount = result.places().stream()
                .map(PlaceExperienceClassifier::classify)
                .map(PlaceExperienceClassifier.ExperienceProfile::type)
                .distinct()
                .count();
        assertThat(result.places()).hasSize(9);
        assertThat(experienceTypeCount).isGreaterThanOrEqualTo(3);
    }

    private ScheduleCreateRequest request() {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"), LocalTime.parse("10:30"),
                location("부산역", "129.0403", "35.1151"),
                location("해운대", "129.1604", "35.1587"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_CULTURE")),
                List.of()
        );
    }

    private ScheduleDay day() {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"), LocalTime.parse("10:30"),
                "부산역", decimal("129.0403"), decimal("35.1151"),
                "해운대", decimal("129.1604"), decimal("35.1587"),
                "test", "{}"
        );
        return new ScheduleDay(
                schedule, 1, LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"), LocalTime.parse("10:30"),
                "부산역", decimal("129.0403"), decimal("35.1151"),
                "해운대", decimal("129.1604"), decimal("35.1587")
        );
    }

    private ScheduleCreateRequest.Location location(String name, String longitude, String latitude) {
        return new ScheduleCreateRequest.Location(name, decimal(longitude), decimal(latitude));
    }

    private Place place(Long id, String name, String contentTypeId, String longitude, String latitude) {
        Place place = new Place(
                "LOCAL_FIXTURE", name, contentTypeId, name, "관광지", "부산",
                decimal(longitude), decimal(latitude), null
        );
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
