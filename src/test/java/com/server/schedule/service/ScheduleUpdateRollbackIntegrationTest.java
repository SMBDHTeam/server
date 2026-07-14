package com.server.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.dto.ScheduleUpdateRequest;
import com.server.schedule.repository.ScheduleRepository;
import com.server.transit.service.TransitRouteProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("일정 수정 트랜잭션")
class ScheduleUpdateRollbackIntegrationTest {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private TransitRouteProvider transitRouteProvider;

    @BeforeEach
    void resetProvider() {
        reset(transitRouteProvider);
    }

    @AfterEach
    void cleanUp() {
        scheduleRepository.deleteAll();
        placeRepository.deleteAll();
    }

    @Test
    @DisplayName("경로 Provider 실패 시 flush된 stop 변경도 모두 롤백한다")
    void providerFailureRollsBackStopChanges() {
        Place firstPlace = place("ROLLBACK-1", "기존 장소 A", "129.1600", "35.1700");
        Place removedPlace = place("ROLLBACK-2", "기존 장소 B", "129.1800", "35.1800");
        Place addedPlace = place("ROLLBACK-3", "추가 장소", "129.2000", "35.1900");
        placeRepository.saveAllAndFlush(List.of(firstPlace, removedPlace, addedPlace));
        Schedule schedule = schedule();
        ScheduleDay day = new ScheduleDay(schedule, 1, LocalDate.parse("2026-08-01"));
        ScheduleStop first = new ScheduleStop(day, firstPlace, 1, 60);
        ScheduleStop removed = new ScheduleStop(day, removedPlace, 2, 60);
        scheduleRepository.saveAndFlush(schedule);
        UUID scheduleId = schedule.getId();
        UUID firstStopId = first.getId();
        UUID removedStopId = removed.getId();
        when(transitRouteProvider.findRoute(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));

        assertThatThrownBy(() -> scheduleService.update(
                scheduleId,
                new ScheduleUpdateRequest(List.of(
                        new ScheduleUpdateRequest.Stop(firstStopId, null, 1, 1, 70),
                        new ScheduleUpdateRequest.Stop(null, addedPlace.getId(), 1, 2, 60)
                ))
        )).isInstanceOf(BusinessException.class);

        transactionTemplate.executeWithoutResult(status -> {
            Schedule reloaded = scheduleRepository.findById(scheduleId).orElseThrow();
            assertThat(reloaded.getDays().get(0).getStops())
                    .extracting(ScheduleStop::getId)
                    .containsExactly(firstStopId, removedStopId);
            assertThat(reloaded.getDays().get(0).getStops())
                    .extracting(ScheduleStop::getStayMinutes)
                    .containsExactly(60, 60);
        });
    }

    private Schedule schedule() {
        return new Schedule(
                LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-01"),
                LocalTime.parse("09:00"), LocalTime.parse("19:00"),
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"),
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"),
                "rollback", "{}"
        );
    }

    private Place place(String externalId, String name, String longitude, String latitude) {
        return new Place(
                "TOUR_API", externalId, "12", name, "관광지", "부산",
                new BigDecimal(longitude), new BigDecimal(latitude), null
        );
    }
}
