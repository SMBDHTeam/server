package com.server.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import com.server.schedule.repository.ScheduleRepository;
import com.server.share.dto.ShareLinkCreateRequest;
import com.server.share.dto.ShareLinkResponse;
import com.server.share.service.ShareService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("일정 생명주기 통합")
class ScheduleLifecycleIntegrationTest {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ShareService shareService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("기존 stop ID를 보존하며 일차 이동·삭제·추가 후 경로를 재계산한다")
    void updateRebuildsScheduleAndRoutes() {
        Fixture fixture = fixture();

        ScheduleResponse response = scheduleService.update(
                fixture.schedule().getId(),
                new ScheduleUpdateRequest(List.of(
                        new ScheduleUpdateRequest.Stop(fixture.first().getId(), null, 1, 1, 70),
                        new ScheduleUpdateRequest.Stop(fixture.moving().getId(), null, 2, 1, 60),
                        new ScheduleUpdateRequest.Stop(null, fixture.addedPlace().getId(), 2, 2, 50)
                ))
        );
        scheduleRepository.flush();
        entityManager.clear();

        Schedule reloaded = scheduleRepository.findById(fixture.schedule().getId()).orElseThrow();
        assertThat(response.evaluation()).isNull();
        assertThat(response.days()).extracting(day -> day.stops().size()).containsExactly(1, 2);
        assertThat(reloaded.getDays().get(0).getStops())
                .extracting(ScheduleStop::getId)
                .containsExactly(fixture.first().getId());
        assertThat(reloaded.getDays().get(1).getStops())
                .extracting(ScheduleStop::getId)
                .contains(fixture.moving().getId())
                .doesNotContain(fixture.removed().getId());
        assertThat(reloaded.getDays())
                .allSatisfy(day -> assertThat(day.getTransitRoutes()).hasSize(day.getStops().size() + 1));
    }

    @Test
    @DisplayName("공유 토큰은 해시로 저장하고 폐기 전까지만 일정과 지도를 조회한다")
    void shareLinkLifecycle() {
        Fixture fixture = fixture();
        scheduleService.update(
                fixture.schedule().getId(),
                new ScheduleUpdateRequest(List.of(
                        new ScheduleUpdateRequest.Stop(fixture.first().getId(), null, 1, 1, 60),
                        new ScheduleUpdateRequest.Stop(fixture.moving().getId(), null, 2, 1, 60)
                ))
        );

        ShareLinkResponse link = shareService.create(
                fixture.schedule().getId(), new ShareLinkCreateRequest(7));
        entityManager.flush();
        String storedHash = jdbcTemplate.queryForObject(
                "select token_hash from share_links where id = ?",
                String.class,
                link.id()
        );

        assertThat(link.token()).isNotBlank();
        assertThat(storedHash).hasSize(64).isNotEqualTo(link.token());
        assertThat(shareService.getSharedSchedule(link.token()).readOnly()).isTrue();
        assertThat(shareService.getSharedMap(link.token(), 1).markers()).hasSize(1);

        shareService.revoke(fixture.schedule().getId(), link.id());

        assertThatThrownBy(() -> shareService.getSharedSchedule(link.token()))
                .isInstanceOf(BusinessException.class);
    }

    private Fixture fixture() {
        Place firstPlace = place("FIRST", "첫 장소", "129.0400", "35.1100");
        Place movingPlace = place("MOVING", "이동 장소", "129.0800", "35.1300");
        Place removedPlace = place("REMOVED", "삭제 장소", "129.1200", "35.1500");
        Place addedPlace = place("ADDED", "추가 장소", "129.1600", "35.1700");
        placeRepository.saveAllAndFlush(List.of(firstPlace, movingPlace, removedPlace, addedPlace));

        Schedule schedule = new Schedule(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-02"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                "부산역",
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                "부산역",
                new BigDecimal("129.0403"),
                new BigDecimal("35.1151"),
                "테스트 일정",
                "{}"
        );
        ScheduleDay firstDay = new ScheduleDay(schedule, 1, LocalDate.parse("2026-08-01"));
        ScheduleDay secondDay = new ScheduleDay(schedule, 2, LocalDate.parse("2026-08-02"));
        ScheduleStop first = new ScheduleStop(firstDay, firstPlace, 1, 60);
        ScheduleStop moving = new ScheduleStop(firstDay, movingPlace, 2, 60);
        ScheduleStop removed = new ScheduleStop(secondDay, removedPlace, 1, 60);
        scheduleRepository.saveAndFlush(schedule);
        return new Fixture(schedule, first, moving, removed, addedPlace);
    }

    private Place place(String externalId, String name, String longitude, String latitude) {
        return new Place(
                "TOUR_API",
                externalId,
                "12",
                name,
                "관광지",
                "부산",
                new BigDecimal(longitude),
                new BigDecimal(latitude),
                null
        );
    }

    private record Fixture(
            Schedule schedule,
            ScheduleStop first,
            ScheduleStop moving,
            ScheduleStop removed,
            Place addedPlace
    ) {
    }
}
