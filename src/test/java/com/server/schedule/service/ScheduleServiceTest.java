package com.server.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.domain.TransitRoute;
import com.server.schedule.domain.TransitRouteLine;
import com.server.schedule.domain.TransitSegment;
import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.repository.ScheduleRepository;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteProvider;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("일정 생성 서비스")
class ScheduleServiceTest {

    private final ScheduleRepository scheduleRepository = Mockito.mock(ScheduleRepository.class);
    private final PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
    private final TransitRouteProvider transitRouteProvider = Mockito.mock(TransitRouteProvider.class);
    private final ScheduleService scheduleService = new ScheduleService(
            scheduleRepository,
            placeRepository,
            transitRouteProvider
    );

    @Test
    @DisplayName("필수 방문 장소와 대중교통 경로를 포함한 일정을 생성한다")
    void createReturnsScheduleWithMustVisitPlacesAndTransitRoutes() {
        Place firstPlace = place(101L, "이송도전망대", "129.047956", "35.075519");
        Place secondPlace = place(205L, "감천문화마을", "129.010652", "35.097486");
        when(placeRepository.findAllById(List.of(101L, 205L)))
                .thenReturn(List.of(firstPlace, secondPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(request(List.of(101L, 205L)));

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.days()).hasSize(2);
        assertThat(response.days().get(0).stops()).hasSize(1);
        assertThat(response.days().get(0).stops().get(0).place().id()).isEqualTo(101L);
        assertThat(response.days().get(0).stops().get(0).inboundTransit().totalMinutes()).isEqualTo(25);
        assertThat(response.days().get(0).finalTransit().segments()).hasSize(1);
        assertThat(response.days().get(1).stops().get(0).place().id()).isEqualTo(205L);
        verify(transitRouteProvider, Mockito.times(4))
                .findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class));

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        Schedule savedSchedule = captor.getValue();
        assertThat(savedSchedule.getDays()).hasSize(2);
        assertThat(savedSchedule.getDays().get(0).getTransitRoutes()).hasSize(2);
    }

    @Test
    @DisplayName("필수 방문 장소가 없으면 내부 장소 후보로 일정을 생성한다")
    void createUsesInternalPlacesWhenMustVisitPlacesAreEmpty() {
        Place firstPlace = place(101L, "이송도전망대", "129.047956", "35.075519");
        Place secondPlace = place(205L, "감천문화마을", "129.010652", "35.097486");
        when(placeRepository.findAll()).thenReturn(List.of(secondPlace, firstPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(request(null));

        assertThat(response.days()).hasSize(2);
        assertThat(response.days().get(0).stops().get(0).place().id()).isEqualTo(101L);
        assertThat(response.days().get(1).stops().get(0).place().id()).isEqualTo(205L);
    }

    @Test
    @DisplayName("필수 방문 장소가 없으면 장소 없음 예외를 던진다")
    void missingMustVisitPlaceThrowsPlaceNotFound() {
        when(placeRepository.findAllById(List.of(101L, 404L)))
                .thenReturn(List.of(place(101L, "이송도전망대", "129.047956", "35.075519")));

        assertThatThrownBy(() -> scheduleService.create(request(List.of(101L, 404L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);

        verify(scheduleRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("장소 후보가 없으면 장소 없음 예외를 던진다")
    void emptyPlaceCandidatesThrowsPlaceNotFound() {
        when(placeRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> scheduleService.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);

        verify(scheduleRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("날짜나 운영 시간이 잘못되면 일정 조건 예외를 던진다")
    void invalidScheduleConditionThrowsBusinessException() {
        ScheduleCreateRequest invalidRequest = new ScheduleCreateRequest(
                LocalDate.parse("2026-06-25"),
                LocalDate.parse("2026-06-23"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS")),
                List.of(101L)
        );

        assertThatThrownBy(() -> scheduleService.create(invalidRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);
    }

    @Test
    @DisplayName("저장된 일정의 지도 마커와 대중교통 경로선을 반환한다")
    void getMapReturnsMarkersAndRouteLines() {
        Schedule schedule = schedule();
        ScheduleDay day = new ScheduleDay(schedule, 1, LocalDate.parse("2026-06-23"));
        ScheduleStop stop = new ScheduleStop(
                day,
                place(101L, "부산타워", "129.032338", "35.101243"),
                1,
                60
        );
        TransitRoute inboundRoute = new TransitRoute(day, stop, "INBOUND", 1, 11, 1600, "{\"provider\":\"ODsay\"}");
        new TransitSegment(inboundRoute, 1, "WALK", null, null, null);
        new TransitSegment(inboundRoute, 2, "SUBWAY", "부산 1호선", "부산역", "중앙역");
        new TransitSegment(inboundRoute, 3, "WALK", null, null, null);
        new TransitRouteLine(
                inboundRoute,
                1,
                "WALK",
                null,
                "[[\"129.0403\",\"35.1151\"],[\"129.039323\",\"35.114494\"]]"
        );
        new TransitRouteLine(
                inboundRoute,
                2,
                "SUBWAY",
                "부산 1호선",
                "[[\"129.039323\",\"35.114494\"],[\"129.03637\",\"35.103937\"]]"
        );
        new TransitRouteLine(
                inboundRoute,
                3,
                "WALK",
                null,
                "[[\"129.03637\",\"35.103937\"],[\"129.032338\",\"35.101243\"]]"
        );
        TransitRoute finalRoute = new TransitRoute(day, null, "FINAL", 2, 11, 1600, "{\"provider\":\"ODsay\"}");
        new TransitSegment(finalRoute, 1, "SUBWAY", "부산 1호선", "중앙역", "부산역");
        new TransitRouteLine(
                finalRoute,
                1,
                "SUBWAY",
                "부산 1호선",
                "[[129.03637,35.103937],[129.039323,35.114494]]"
        );
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        ScheduleMapResponse response = scheduleService.getMap(schedule.getId(), 1);

        assertThat(response.startMarker().name()).isEqualTo("부산역");
        assertThat(response.endMarker().name()).isEqualTo("부산역");
        assertThat(response.markers()).hasSize(1);
        assertThat(response.markers().get(0).placeId()).isEqualTo(101L);
        assertThat(response.routeLines()).hasSize(4);
        assertThat(response.routeLines().get(0).routeOrder()).isEqualTo(1);
        assertThat(response.routeLines().get(0).mode()).isEqualTo("WALK");
        assertThat(response.routeLines().get(0).startName()).isEqualTo("부산역");
        assertThat(response.routeLines().get(0).endName()).isEqualTo("부산역");
        assertThat(response.routeLines().get(1).mode()).isEqualTo("SUBWAY");
        assertThat(response.routeLines().get(1).startName()).isEqualTo("부산역");
        assertThat(response.routeLines().get(1).endName()).isEqualTo("중앙역");
        assertThat(response.routeLines().get(2).mode()).isEqualTo("WALK");
        assertThat(response.routeLines().get(2).startName()).isEqualTo("중앙역");
        assertThat(response.routeLines().get(2).endName()).isEqualTo("부산타워");
        assertThat(response.routeLines().get(0).coordinates()).containsExactly(
                List.of(new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                List.of(new BigDecimal("129.039323"), new BigDecimal("35.114494"))
        );
        assertThat(response.routeLines().get(1).coordinates()).containsExactly(
                List.of(new BigDecimal("129.039323"), new BigDecimal("35.114494")),
                List.of(new BigDecimal("129.03637"), new BigDecimal("35.103937"))
        );
        assertThat(response.routeLines().get(3).routeOrder()).isEqualTo(2);
        assertThat(response.routeLines().get(3).startName()).isEqualTo("중앙역");
        assertThat(response.routeLines().get(3).endName()).isEqualTo("부산역");
    }

    @Test
    @DisplayName("일정 지도 조회 시 일정이 없으면 일정 없음 예외를 던진다")
    void getMapThrowsScheduleNotFound() {
        UUID scheduleId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getMap(scheduleId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    private ScheduleCreateRequest request(List<Long> mustVisitPlaceIds) {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-24"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS")),
                mustVisitPlaceIds
        );
    }

    private Schedule schedule() {
        return new Schedule(
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-23"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                "부산역",
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                "부산역",
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                "COMPANION:COMPANION_PARENTS",
                "{}"
        );
    }

    private Place place(Long id, String name, String longitude, String latitude) {
        Place place = new Place(
                "TOUR_API",
                "content-" + id,
                "12",
                name,
                "관광지",
                "부산",
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                null
        );
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private TransitRouteResult route() {
        return new TransitRouteResult(
                25,
                1550,
                List.of(new TransitRouteResult.Segment("BUS", "26", "부산역", "남부민2동")),
                List.of(new TransitRouteResult.RouteLine("BUS", "26", "[[129.0,35.0],[129.1,35.1]]")),
                "{\"provider\":\"FAKE\"}"
        );
    }
}
