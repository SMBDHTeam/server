package com.server.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.metrics.ExternalCallMetricsCollector;
import com.server.external.openai.AiScheduleProposalClient;
import com.server.external.openai.OpenAiPlanningProperties;
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
import com.server.schedule.dto.ScheduleUpdateRequest;
import com.server.schedule.evaluation.ScheduleHardGateEvaluator;
import com.server.schedule.evaluation.ScheduleScoreEvaluator;
import com.server.schedule.evaluation.ScheduleScoreResult;
import com.server.schedule.planner.AiSchedulePlanGenerator;
import com.server.schedule.planner.DayPlaceAllocator;
import com.server.schedule.planner.DayRouteOptimizer;
import com.server.schedule.planner.FixedEventPlanner;
import com.server.schedule.planner.MultiDayPlanOptimizer;
import com.server.schedule.planner.PlaceCandidateProvider;
import com.server.schedule.planner.PlacePreferenceScorer;
import com.server.schedule.planner.PlannerRouteEstimator;
import com.server.schedule.planner.ScheduleFeasibilityChecker;
import com.server.schedule.planner.SchedulePlannerProperties;
import com.server.schedule.repository.ScheduleRepository;
import com.server.transit.service.TransitPoint;
import com.server.transit.service.TransitRouteEstimate;
import com.server.transit.service.TransitRouteProvider;
import com.server.transit.service.TransitRouteResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final TransitRouteProvider transitRouteProvider = Mockito.mock(
            TransitRouteProvider.class,
            Mockito.CALLS_REAL_METHODS
    );
    private final ScheduleService scheduleService = service(disabledAiGenerator());

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
        assertThat(response.dailyStartTime()).isEqualTo(LocalTime.parse("09:00"));
        assertThat(response.dailyEndTime()).isEqualTo(LocalTime.parse("19:00"));
        assertThat(response.days()).hasSize(2);
        assertThat(response.days().get(0).stops()).hasSize(1);
        assertThat(response.days().get(0).stops().get(0).place().id()).isEqualTo(101L);
        assertThat(response.days().get(0).stops().get(0).arriveAt()).isEqualTo(LocalTime.parse("09:25"));
        assertThat(response.days().get(0).stops().get(0).departAt()).isEqualTo(LocalTime.parse("10:25"));
        ScheduleResponse.Stop mustVisitStop = response.days().get(0).stops().stream()
                .filter(stop -> stop.place().id().equals(101L))
                .findFirst()
                .orElseThrow();
        assertThat(mustVisitStop.selectionReasons())
                .contains("사용자가 반드시 방문할 장소로 선택했습니다.");
        assertThat(response.days().get(0).stops().get(0).inboundTransit().totalMinutes()).isEqualTo(25);
        assertThat(response.days().get(0).stops().get(0).inboundTransit().routeType()).isEqualTo("INBOUND");
        assertThat(response.days().get(0).stops().get(0).inboundTransit().originName()).isEqualTo("부산역");
        assertThat(response.days().get(0).stops().get(0).inboundTransit().destinationName()).isEqualTo("이송도전망대");
        assertThat(response.days().get(0).stops().get(0).inboundTransit().segments().get(0).instruction())
                .isEqualTo("부산역에서 26 승차 후 남부민2동에서 하차");
        assertThat(response.days().get(0).finalTransit().segments()).hasSize(1);
        assertThat(response.days().get(1).stops().get(0).place().id()).isEqualTo(205L);
        assertThat(response.evaluation().hardGate().passed()).isTrue();
        assertThat(response.evaluation().hardGate().violations()).isEmpty();
        assertThat(response.evaluation().qualityScore().maxScore()).isEqualTo(100);
        assertThat(response.evaluation().operations().providerCallCount()).isZero();
        assertThat(response.evaluation().operations().providerFailureCount()).isZero();
        assertThat(response.evaluation().operations().routeCount()).isEqualTo(4);
        verify(transitRouteProvider, Mockito.times(4))
                .findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class));

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        Schedule savedSchedule = captor.getValue();
        assertThat(savedSchedule.getDays()).hasSize(2);
        assertThat(savedSchedule.getDays().get(0).getTransitRoutes()).hasSize(2);
    }

    @Test
    @DisplayName("좌표 기준 상위 순서를 실제 경로로 재평가한다")
    void createOptimizesVisitOrderWithRouteCache() {
        Place placeA = place(101L, "A", "129.0800", "35.1500");
        Place placeB = place(102L, "B", "129.1200", "35.1800");
        when(placeRepository.findAllById(List.of(101L, 102L))).thenReturn(List.of(placeA, placeB));
        when(placeRepository.findAll()).thenReturn(List.of());
        Map<String, Integer> routeMinutes = Map.of(
                "부산역>A", 50,
                "부산역>B", 10,
                "A>B", 50,
                "B>A", 10,
                "A>부산역", 10,
                "B>부산역", 50
        );
        Mockito.doAnswer(invocation -> {
            TransitPoint origin = invocation.getArgument(0);
            TransitPoint destination = invocation.getArgument(1);
            return TransitRouteEstimate.estimated(
                    route(routeMinutes.get(origin.name() + ">" + destination.name())),
                    new TransitRouteEstimate.DetailContext() { });
        }).when(transitRouteProvider).findRouteEstimate(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> ((TransitRouteEstimate) invocation.getArgument(2)).route())
                .when(transitRouteProvider).findRouteDetail(Mockito.any(), Mockito.any(), Mockito.any());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(List.of(101L, 102L)));

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().name())
                .containsExactly("B", "A");
        assertThat(response.days().get(0).finalTransit().totalMinutes()).isEqualTo(10);
        assertThat(response.evaluation().operations().providerEstimateCallCount()).isEqualTo(6);
        assertThat(response.evaluation().operations().routeResolutionCount()).isEqualTo(3);
        assertThat(response.evaluation().operations().routeCacheHitCount()).isZero();
        assertThat(response.evaluation().operations().providerCallCount()).isEqualTo(3);
        verify(transitRouteProvider, Mockito.times(6)).findRouteEstimate(Mockito.any(), Mockito.any());
        verify(transitRouteProvider, Mockito.times(3))
                .findRouteDetail(Mockito.any(), Mockito.any(), Mockito.any());
        verify(transitRouteProvider, never()).findRoute(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("상위 순서는 경량 경로로 비교하고 선택된 구간만 상세 조회한다")
    void createUsesEstimateDuringOptimizationAndDetailsSelectedRoutes() {
        Place firstPlace = place(101L, "A", "129.0800", "35.1500");
        Place secondPlace = place(102L, "B", "129.1200", "35.1800");
        when(placeRepository.findAllById(List.of(101L, 102L))).thenReturn(List.of(firstPlace, secondPlace));
        Mockito.doReturn(TransitRouteEstimate.estimated(
                        route(), new TransitRouteEstimate.DetailContext() { }))
                .when(transitRouteProvider).findRouteEstimate(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> ((TransitRouteEstimate) invocation.getArgument(2)).route())
                .when(transitRouteProvider).findRouteDetail(Mockito.any(), Mockito.any(), Mockito.any());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(List.of(101L, 102L)));

        assertThat(response.days().get(0).stops()).hasSize(2);
        assertThat(response.evaluation().operations().providerEstimateCallCount()).isEqualTo(6);
        assertThat(response.evaluation().operations().routeResolutionCount()).isEqualTo(3);
        verify(transitRouteProvider, Mockito.times(6)).findRouteEstimate(Mockito.any(), Mockito.any());
        verify(transitRouteProvider, Mockito.times(3))
                .findRouteDetail(Mockito.any(), Mockito.any(), Mockito.any());
        verify(transitRouteProvider, never()).findRoute(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("AI가 선택한 일차별 장소 구성을 실제 일정에 반영한다")
    void createUsesAiGeneratedPlaceAssignment() {
        Place deterministicFirst = place(101L, "규칙 후보", "129.0600", "35.1300");
        Place aiSelected = place(102L, "AI 선택 후보", "129.1700", "35.1800");
        when(placeRepository.findAll()).thenReturn(List.of(deterministicFirst, aiSelected));
        when(transitRouteProvider.findRoute(Mockito.any(), Mockito.any())).thenReturn(route(10));
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AiScheduleProposalClient client = proposalRequest -> {
            long selectedId = proposalRequest.candidates().stream()
                    .filter(candidate -> "AI 선택 후보".equals(candidate.name()))
                    .findFirst()
                    .orElseThrow()
                    .placeId();
            return new AiScheduleProposalClient.Proposal(
                    List.of(new AiScheduleProposalClient.DayProposal(1, List.of(selectedId))),
                    96,
                    "사용자 선호에 맞는 장소 선택"
            );
        };
        AiSchedulePlanGenerator generator = new AiSchedulePlanGenerator(
                client,
                new OpenAiPlanningProperties(
                        true, "http://localhost", "test-key", "test-model", null, null)
        );
        ScheduleService aiScheduleService = service(generator);

        ScheduleResponse response = aiScheduleService.create(twoHourRequest(
                List.of(new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_RELAXED")),
                List.of()
        ));

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().id())
                .containsExactly(102L);
        assertThat(response.evaluation().operations().planningMode()).isEqualTo("AI_GENERATED");
        assertThat(response.evaluation().operations().aiPlanConfidence()).isEqualTo(96);
        assertThat(response.evaluation().hardGate().passed()).isTrue();
    }

    @Test
    @DisplayName("다일 장소 배치안을 실제 경로로 재평가해 날짜 배정을 변경한다")
    void createReranksMultiDayAssignmentsWithActualRoutes() {
        Place westA = place(101L, "A", "129.0300", "35.1000");
        Place westB = place(102L, "B", "129.0400", "35.1100");
        Place eastC = place(103L, "C", "129.1700", "35.1600");
        when(placeRepository.findAllById(List.of(101L, 102L, 103L)))
                .thenReturn(List.of(westA, westB, eastC));
        Mockito.doAnswer(invocation -> {
            TransitPoint origin = invocation.getArgument(0);
            TransitPoint destination = invocation.getArgument(1);
            int minutes = preferredMultiDayRoute(origin.name(), destination.name()) ? 5 : 80;
            return TransitRouteEstimate.estimated(
                    route(minutes), new TransitRouteEstimate.DetailContext() { });
        }).when(transitRouteProvider).findRouteEstimate(Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> ((TransitRouteEstimate) invocation.getArgument(2)).route())
                .when(transitRouteProvider).findRouteDetail(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> {
            TransitPoint origin = invocation.getArgument(0);
            TransitPoint destination = invocation.getArgument(1);
            return route(preferredMultiDayRoute(origin.name(), destination.name()) ? 5 : 80);
        }).when(transitRouteProvider).findRoute(Mockito.any(), Mockito.any());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleCreateRequest request = new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-21"),
                LocalTime.parse("09:00"),
                LocalTime.parse("13:00"),
                location("서부 출발", "128.9900", "35.1000"),
                location("동부 도착", "129.2100", "35.1650"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS")),
                List.of(101L, 102L, 103L),
                List.of(
                        dayCondition(1, "09:00", "13:00",
                                location("서부 출발", "128.9900", "35.1000"),
                                location("서부 도착", "128.9950", "35.1100")),
                        dayCondition(2, "09:00", "11:00",
                                location("동부 출발", "129.2050", "35.1550"),
                                location("동부 도착", "129.2100", "35.1650"))
                )
        );

        ScheduleResponse response = scheduleService.create(request);

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().name())
                .containsExactlyInAnyOrder("B", "C");
        assertThat(response.days().get(1).stops())
                .extracting(stop -> stop.place().name())
                .containsExactly("A");
        assertThat(response.evaluation().operations().multiDayPlanCandidateCount()).isEqualTo(3);
        assertThat(response.evaluation().operations().multiDayPlanRerankedCount()).isEqualTo(3);
        assertThat(response.evaluation().operations().providerEstimateCallCount()).isLessThanOrEqualTo(30);
    }

    private boolean preferredMultiDayRoute(String origin, String destination) {
        return ("서부 출발".equals(origin) && Set.of("B", "C").contains(destination))
                || (Set.of("B", "C").contains(origin) && Set.of("B", "C", "서부 도착").contains(destination))
                || ("동부 출발".equals(origin) && "A".equals(destination))
                || ("A".equals(origin) && "동부 도착".equals(destination));
    }

    @Test
    @DisplayName("다일 일정은 일차별 출발지와 도착지를 기준으로 경로를 생성한다")
    void createUsesDaySpecificEndpoints() {
        Place firstPlace = place(101L, "첫날 장소", "129.0800", "35.1200");
        Place secondPlace = place(102L, "둘째 날 장소", "129.1200", "35.1600");
        Place thirdPlace = place(103L, "마지막 날 장소", "128.9900", "35.1800");
        when(placeRepository.findAllById(List.of(101L, 102L, 103L)))
                .thenReturn(List.of(firstPlace, secondPlace, thirdPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleCreateRequest request = new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-22"),
                LocalTime.parse("09:00"),
                LocalTime.parse("20:00"),
                location("부산역", "129.0403", "35.1151"),
                location("김해국제공항", "128.9485", "35.1732"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS")),
                List.of(101L, 102L, 103L),
                List.of(
                        dayCondition(1, "11:00", "20:00", location("부산역", "129.0403", "35.1151"), location("숙소 A", "129.1580", "35.1590")),
                        dayCondition(2, "09:00", "20:00", location("숙소 A", "129.1580", "35.1590"), location("숙소 B", "129.0320", "35.1000")),
                        dayCondition(3, "09:00", "17:00", location("숙소 B", "129.0320", "35.1000"), location("김해국제공항", "128.9485", "35.1732"))
                )
        );

        ScheduleResponse response = scheduleService.create(request);

        assertThat(response.days()).extracting(day -> day.startLocation().name())
                .containsExactly("부산역", "숙소 A", "숙소 B");
        assertThat(response.days()).extracting(day -> day.endLocation().name())
                .containsExactly("숙소 A", "숙소 B", "김해국제공항");
        assertThat(response.days()).extracting(day -> day.stops().get(0).inboundTransit().originName())
                .containsExactly("부산역", "숙소 A", "숙소 B");
        assertThat(response.days()).extracting(day -> day.finalTransit().destinationName())
                .containsExactly("숙소 A", "숙소 B", "김해국제공항");
    }

    @Test
    @DisplayName("필수 방문 장소를 유지하면서 남은 일정 슬롯은 자동 추천으로 채운다")
    void createCombinesMustVisitAndRecommendedPlaces() {
        Place mustVisit = place(101L, "필수 장소", "12", "129.0200", "35.1000");
        Place recommendationA = place(102L, "추천 행사", "15", "129.0500", "35.1300");
        Place recommendationB = place(103L, "추천 음식점", "39", "129.0800", "35.1600");
        when(placeRepository.findAllById(List.of(101L))).thenReturn(List.of(mustVisit));
        when(placeRepository.findAll()).thenReturn(List.of(mustVisit, recommendationA, recommendationB));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(List.of(101L)));

        assertThat(response.days().get(0).stops()).hasSize(3);
        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().id())
                .containsExactlyInAnyOrder(101L, 102L, 103L);
        ScheduleResponse.Stop mustVisitStop = response.days().get(0).stops().stream()
                .filter(stop -> stop.place().id().equals(101L))
                .findFirst()
                .orElseThrow();
        assertThat(mustVisitStop.selectionReasons())
                .contains("사용자가 반드시 방문할 장소로 선택했습니다.");
        ScheduleResponse.Stop mealStop = response.days().get(0).stops().stream()
                .filter(stop -> stop.place().id().equals(103L))
                .findFirst()
                .orElseThrow();
        assertThat(mealStop.selectionReasons())
                .contains(
                        "출발지와 도착지 기준 동선 점수가 높은 장소입니다.",
                        "점심 또는 저녁 식사 시간대에 이용할 수 있는 장소입니다.");
        assertThat(mealStop.arriveAt())
                .isBetween(LocalTime.parse("11:00"), LocalTime.parse("14:00"));
    }

    @Test
    @DisplayName("자동 추천은 상세 동기화가 완료된 장소만 사용한다")
    void createExcludesPlacesPendingIngestion() {
        Place mustVisit = place(101L, "필수 장소", "12", "129.0200", "35.1000");
        Place syncedRecommendation = place(102L, "동기화된 추천 장소", "15", "129.0500", "35.1300");
        Place pendingRecommendation = place(103L, "적재 대기 장소", "39", "129.0800", "35.1600");
        pendingRecommendation.markNewDiscovery(LocalDateTime.now(), LocalDateTime.now());
        when(placeRepository.findAllById(List.of(101L))).thenReturn(List.of(mustVisit));
        when(placeRepository.findAll()).thenReturn(List.of(mustVisit, syncedRecommendation, pendingRecommendation));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(List.of(101L)));

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().id())
                .contains(101L, 102L)
                .doesNotContain(103L);
    }

    @Test
    @DisplayName("실제 이동과 체류시간이 일차 종료시각을 넘으면 생성을 거부한다")
    void createRejectsDayTimeOverrun() {
        Place place = place(101L, "필수 장소", "129.0800", "35.1600");
        when(placeRepository.findAllById(List.of(101L))).thenReturn(List.of(place));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());

        ScheduleCreateRequest request = new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-20"),
                LocalTime.parse("09:00"),
                LocalTime.parse("20:00"),
                location("부산역", "129.0403", "35.1151"),
                location("부산역", "129.0403", "35.1151"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS")),
                List.of(101L),
                List.of(dayCondition(1, "09:00", "10:00", location("부산역", "129.0403", "35.1151"), location("숙소", "129.1000", "35.1800")))
        );

        assertThatThrownBy(() -> scheduleService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);
        verify(scheduleRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("일차별 조건을 전달하면 여행 기간의 모든 일차가 있어야 한다")
    void createRejectsIncompleteDayConditions() {
        ScheduleCreateRequest request = new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"),
                LocalDate.parse("2026-07-21"),
                LocalTime.parse("09:00"),
                LocalTime.parse("20:00"),
                location("부산역", "129.0403", "35.1151"),
                location("김해국제공항", "128.9485", "35.1732"),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS")),
                List.of(),
                List.of(dayCondition(1, "09:00", "20:00", location("부산역", "129.0403", "35.1151"), location("숙소", "129.1000", "35.1800")))
        );

        assertThatThrownBy(() -> scheduleService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);
        verify(placeRepository, never()).findAll();
        verify(scheduleRepository, never()).save(Mockito.any());
    }

    @Test
    @DisplayName("필수 방문 장소가 없으면 내부 장소 후보를 날짜별로 분산해 일정을 생성한다")
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
        assertThat(response.days())
                .flatExtracting(ScheduleResponse.Day::stops)
                .extracting(stop -> stop.place().id())
                .containsExactlyInAnyOrder(101L, 205L);
    }

    @Test
    @DisplayName("필수 방문 장소가 없으면 테스트명을 제외하고 콘텐츠 타입을 섞어 후보를 고른다")
    void createAutoSelectsDiverseInternalPlaces() {
        Place testPlace = place(1L, "광안리해수욕장 테스트", "12", "129.0327", "35.1000");
        Place touristPlace = place(2L, "광복로패션거리", "12", "129.0327", "35.1000");
        Place festivalPlace = place(3L, "부산 스트릿 페스타", "15", "129.0200", "35.1000");
        Place shoppingPlace = place(4L, "남포 지하상가", "38", "129.0450", "35.1000");
        when(placeRepository.findAll()).thenReturn(List.of(testPlace, touristPlace, festivalPlace, shoppingPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(null));

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().id())
                .containsExactlyInAnyOrder(2L, 3L, 4L);
        assertThat(response.days().get(0).stops())
                .extracting(ScheduleResponse.Stop::stayMinutes)
                .containsExactlyInAnyOrder(60, 90, 60);
    }

    @Test
    @DisplayName("부모님 일정은 서부산 동선에서도 부담 장소를 제외한다")
    void createAutoSelectsNeighborhoodRouteNearBusanStation() {
        Place nampoPlace = place(2L, "광복로패션거리", "12", "129.0327", "35.1000");
        Place gamcheonPlace = place(3L, "감천문화마을", "12", "129.0107", "35.0975");
        Place songdoPlace = place(4L, "송도구름산책로", "12", "129.0176", "35.0763");
        Place haeundaePlace = place(5L, "해운대해수욕장", "12", "129.1604", "35.1587");
        when(placeRepository.findAll()).thenReturn(List.of(haeundaePlace, songdoPlace, gamcheonPlace, nampoPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(null));

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().id())
                .containsExactlyInAnyOrder(2L, 4L, 5L)
                .doesNotContain(3L);
    }

    @Test
    @DisplayName("저도보 조건에서는 장소 수보다 부담 장소 제외를 우선한다")
    void createAutoDistributesPartialCandidatesAcrossDays() {
        Place nampoPlace = place(2L, "광복로패션거리", "12", "129.0327", "35.1000");
        Place gamcheonPlace = place(3L, "감천문화마을", "12", "129.0107", "35.0975");
        Place songdoPlace = place(4L, "송도구름산책로", "12", "129.0176", "35.0763");
        Place gwangalliPlace = place(5L, "광안리해수욕장", "12", "129.1187", "35.1532");
        when(placeRepository.findAll()).thenReturn(List.of(nampoPlace, gamcheonPlace, songdoPlace, gwangalliPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(threeDayRequest(null));

        assertThat(response.days()).hasSize(3);
        assertThat(response.days())
                .extracting(day -> day.stops().size())
                .containsExactly(1, 1, 1);
        assertThat(response.days())
                .flatExtracting(ScheduleResponse.Day::stops)
                .extracting(stop -> stop.place().id())
                .doesNotContain(3L);
    }

    @Test
    @DisplayName("부모님 동행은 먼 관광지보다 가까운 저부담 후보를 우선한다")
    void createAutoWeightsWalkingBurdenForParents() {
        Place farTouristPlace = place(2L, "해운대해수욕장", "12", "129.1604", "35.1587");
        Place closeRestaurantPlace = place(3L, "남포 로컬 식당", "39", "129.0327", "35.1000");
        when(placeRepository.findAll()).thenReturn(List.of(farTouristPlace, closeRestaurantPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(twoHourRequest(
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS")),
                null
        ));

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().id())
                .containsExactly(3L);
    }

    @Test
    @DisplayName("종일 일정은 음식점 후보를 점심과 저녁 시간대에 배치한다")
    void createPlacesMealRecommendationsAtLunchAndDinner() {
        Place attraction = place(61L, "송도구름산책로", "12", "129.0176", "35.0763");
        Place lunchPlace = place(62L, "남포 로컬 식당", "39", "129.0327", "35.1000");
        Place dinnerPlace = place(63L, "광안리 저녁 맛집", "39", "129.1187", "35.1532");
        Place alternative = place(64L, "부산근현대역사관", "14", "129.0370", "35.1030");
        Place secondAttraction = place(65L, "태종대유원지", "12", "129.0870", "35.0530");
        when(placeRepository.findAll()).thenReturn(List.of(
                alternative, dinnerPlace, attraction, lunchPlace, secondAttraction));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(packedOneDayRequest());
        List<ScheduleResponse.Stop> mealStops = response.days().get(0).stops().stream()
                .filter(stop -> stop.place().name().contains("식당") || stop.place().name().contains("맛집"))
                .toList();

        assertThat(response.days().get(0).stops()).hasSize(5);
        assertThat(mealStops).hasSize(2);
        assertThat(response.days().get(0).stops()).filteredOn(stop -> stop.mealTimeSlot() == null)
                .hasSize(3);
        assertThat(mealStops.get(0).arriveAt())
                .isBetween(LocalTime.parse("11:00"), LocalTime.parse("14:00"));
        assertThat(mealStops.get(1).arriveAt())
                .isBetween(LocalTime.parse("17:00"), LocalTime.parse("19:00"));
        assertThat(mealStops).extracting(ScheduleResponse.Stop::mealTimeSlot)
                .containsExactly("LUNCH", "DINNER");
        assertThat(mealStops).extracting(ScheduleResponse.Stop::waitingMinutesBefore)
                .allMatch(minutes -> minutes >= 0);
        assertThat(mealStops)
                .allSatisfy(stop -> assertThat(stop.selectionReasons())
                        .contains("점심 또는 저녁 식사 시간대에 이용할 수 있는 장소입니다."));
        assertThat(response.evaluation().hardGate().passed()).isTrue();
        assertThat(response.evaluation().qualityScore().unusedMinutes())
                .isGreaterThanOrEqualTo(mealStops.stream()
                        .mapToInt(ScheduleResponse.Stop::waitingMinutesBefore)
                        .sum());
    }

    @Test
    @DisplayName("실제 경로가 길면 필수 조건을 유지하면서 선택 방문지를 줄여 일정을 완성한다")
    void createReducesOptionalStopsWhenDetailedRoutesDoNotFit() {
        Place attraction = place(71L, "송도구름산책로", "12", "129.0176", "35.0763");
        Place lunchPlace = place(72L, "남포 로컬 식당", "39", "129.0327", "35.1000");
        Place dinnerPlace = place(73L, "광안리 저녁 맛집", "39", "129.1187", "35.1532");
        Place museum = place(74L, "부산근현대역사관", "14", "129.0370", "35.1030");
        Place park = place(75L, "태종대유원지", "12", "129.0870", "35.0530");
        when(placeRepository.findAll()).thenReturn(List.of(
                museum, dinnerPlace, attraction, lunchPlace, park));
        when(transitRouteProvider.findRoute(Mockito.any(), Mockito.any()))
                .thenReturn(route(150));
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(null));

        assertThat(response.days().get(0).stops())
                .isNotEmpty()
                .hasSizeLessThan(5);
        assertThat(response.evaluation().hardGate().passed()).isTrue();
        assertThat(response.evaluation().qualityScore().unusedMinutes()).isGreaterThanOrEqualTo(0);
        verify(scheduleRepository).save(Mockito.any(Schedule.class));
    }

    @Test
    @DisplayName("활동적인 여행도 먼 콘텐츠보다 가까운 후보를 우선한다")
    void createAutoPrioritizesCompactRouteForActiveTravel() {
        Place farTouristPlace = place(2L, "광안리해수욕장", "12", "129.1187", "35.1532");
        Place closeRestaurantPlace = place(3L, "남포 로컬 식당", "39", "129.0327", "35.1000");
        when(placeRepository.findAll()).thenReturn(List.of(closeRestaurantPlace, farTouristPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(twoHourRequest(
                List.of(
                        new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                        new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_ACTIVE")
                ),
                null
        ));

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().id())
                .containsExactly(3L);
    }

    @Test
    @DisplayName("기획 시나리오 질문 답변을 반영해 저부담 로컬 일정을 생성하고 점수 기준을 통과한다")
    void createAndScoreParentRelaxedLocalScenario() {
        Place gamcheonHillPlace = place(10L, "감천문화마을", "12", "129.0107", "35.0975");
        Place localMarketPlace = place(11L, "남포 로컬 시장", "38", "129.0250", "35.1000");
        Place historyPlace = place(12L, "부산근현대역사관", "14", "129.0370", "35.1030");
        Place localCafePlace = place(13L, "부산 로컬 골목 카페", "39", "129.0480", "35.1070");
        Place haeundaePlace = place(14L, "해운대해수욕장", "12", "129.1604", "35.1587");
        ScheduleCreateRequest scenario = oneDayScenarioRequest(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS"),
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_RELAXED"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_LOCAL"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_AVOID_HILLS_STAIRS"),
                new ScheduleCreateRequest.SelectedAnswer("TRANSIT", "TRANSIT_SIMPLE")
        ));
        when(placeRepository.findAll()).thenReturn(List.of(
                gamcheonHillPlace,
                haeundaePlace,
                historyPlace,
                localCafePlace,
                localMarketPlace
        ));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(scenario);
        ScheduleScoreResult score = new ScheduleScoreEvaluator().evaluate(scenario, response);

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().name())
                .doesNotContain("감천문화마을")
                .contains("남포 로컬 시장", "부산 로컬 골목 카페");
        assertThat(score.totalScore()).as(score.toString()).isGreaterThanOrEqualTo(85);
        assertThat(response.evaluation().qualityScore().totalScore()).isEqualTo(score.totalScore());
        assertThat(score.metrics())
                .extracting(ScheduleScoreResult.Metric::id)
                .containsExactly("TIME_FIT", "MOBILITY_FIT", "TRANSIT_FIT", "PREFERENCE_FIT", "ENDPOINT_FIT");
    }

    @Test
    @DisplayName("활동형 자연 시나리오는 근거리 자연 후보를 남기고 점수 기준을 통과한다")
    void createAndScoreActiveNatureFastScenario() {
        Place localMarketPlace = place(21L, "남포 로컬 시장", "38", "129.0250", "35.1000");
        Place restaurantPlace = place(22L, "부산 로컬 식당", "39", "129.0327", "35.1000");
        Place gwangalliBeachPlace = place(23L, "광안리해수욕장", "12", "129.1187", "35.1532");
        Place songdoTrailPlace = place(24L, "송도구름산책로", "12", "129.0176", "35.0763");
        Place historyPlace = place(25L, "부산근현대역사관", "14", "129.0370", "35.1030");
        ScheduleCreateRequest scenario = oneDayScenarioRequest(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_FRIENDS"),
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_ACTIVE"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_NATURE"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_OK_HILLS"),
                new ScheduleCreateRequest.SelectedAnswer("TRANSIT", "TRANSIT_FAST")
        ));
        when(placeRepository.findAll()).thenReturn(List.of(
                restaurantPlace,
                localMarketPlace,
                historyPlace,
                gwangalliBeachPlace,
                songdoTrailPlace
        ));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(scenario);
        ScheduleScoreResult score = new ScheduleScoreEvaluator().evaluate(scenario, response);

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().name())
                .contains("송도구름산책로")
                .doesNotContain("광안리해수욕장");
        assertThat(score.totalScore()).as(score.toString()).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("맛집 선호는 시간과 동선 제약을 넘지 않고 점수 기준을 통과한다")
    void createAndScoreFoodScenario() {
        Place foodPlace = place(31L, "부산 로컬 맛집", "39", "129.0327", "35.1000");
        Place historyPlace = place(32L, "부산근현대역사관", "14", "129.0370", "35.1030");
        Place trailPlace = place(33L, "송도구름산책로", "12", "129.0176", "35.0763");
        ScheduleCreateRequest scenario = twoHourRequest(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_SOLO"),
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_RELAXED"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_FOOD"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_NORMAL"),
                new ScheduleCreateRequest.SelectedAnswer("TRANSIT", "TRANSIT_SIMPLE")
        ), null);
        when(placeRepository.findAll()).thenReturn(List.of(historyPlace, trailPlace, foodPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(scenario);
        ScheduleScoreResult score = new ScheduleScoreEvaluator().evaluate(scenario, response);

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().name())
                .containsExactly("부산근현대역사관");
        assertThat(response.days().get(0).stops().get(0).stayMinutes()).isEqualTo(60);
        assertThat(score.totalScore()).as(score.toString()).isGreaterThanOrEqualTo(85);
    }

    @Test
    @DisplayName("행사 테마는 고정 행사가 아닐 때 동선보다 우선하지 않는다")
    void createAndScoreEventScenario() {
        Place eventPlace = place(41L, "부산 바다 페스타", "15", "129.0200", "35.1000");
        Place marketPlace = place(42L, "남포 로컬 시장", "38", "129.0250", "35.1000");
        Place restaurantPlace = place(43L, "부산 로컬 식당", "39", "129.0327", "35.1000");
        ScheduleCreateRequest scenario = twoHourRequest(List.of(
                new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_COUPLE"),
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_BALANCED"),
                new ScheduleCreateRequest.SelectedAnswer("THEME", "THEME_EVENT"),
                new ScheduleCreateRequest.SelectedAnswer("MOBILITY", "MOBILITY_NORMAL"),
                new ScheduleCreateRequest.SelectedAnswer("TRANSIT", "TRANSIT_TRANSFER_OK")
        ), null);
        when(placeRepository.findAll()).thenReturn(List.of(marketPlace, restaurantPlace, eventPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenReturn(route());
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(scenario);
        ScheduleScoreResult score = new ScheduleScoreEvaluator().evaluate(scenario, response);

        assertThat(response.days().get(0).stops())
                .extracting(stop -> stop.place().name())
                .containsExactly("부산 로컬 식당");
        assertThat(response.days().get(0).stops().get(0).stayMinutes()).isEqualTo(70);
        assertThat(score.totalScore()).as(score.toString()).isGreaterThanOrEqualTo(80);
    }

    @Test
    @DisplayName("초근거리 구간은 외부 대중교통 API 호출 없이 도보 경로로 저장한다")
    void createUsesWalkFallbackForCloseRoute() {
        Place closePlace = place(101L, "부산역 광장", "129.0404", "35.1152");
        when(placeRepository.findAllById(List.of(101L))).thenReturn(List.of(closePlace));
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(List.of(101L)));

        assertThat(response.days().get(0).stops().get(0).inboundTransit().fareAmount()).isNull();
        assertThat(response.days().get(0).stops().get(0).inboundTransit().segments())
                .extracting(ScheduleResponse.Segment::mode)
                .containsExactly("WALK");
        assertThat(response.days().get(0).finalTransit().segments())
                .extracting(ScheduleResponse.Segment::mode)
                .containsExactly("WALK");
        verify(transitRouteProvider, never()).findRoute(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("가까운 구간에서 Provider가 실패하면 전체 일정 실패 대신 도보 fallback을 저장한다")
    void createFallsBackToWalkWhenProviderFailsForNearbyRoute() {
        Place nearbyPlace = place(101L, "부산역 근처", "129.0543", "35.1151");
        when(placeRepository.findAllById(List.of(101L))).thenReturn(List.of(nearbyPlace));
        when(transitRouteProvider.findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class)))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));
        when(scheduleRepository.save(Mockito.any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponse response = scheduleService.create(oneDayRequest(List.of(101L)));

        assertThat(response.days().get(0).stops().get(0).inboundTransit().segments())
                .extracting(ScheduleResponse.Segment::mode)
                .containsExactly("WALK");
        assertThat(response.days().get(0).finalTransit().segments())
                .extracting(ScheduleResponse.Segment::mode)
                .containsExactly("WALK");
        verify(transitRouteProvider, Mockito.times(2))
                .findRoute(Mockito.any(TransitPoint.class), Mockito.any(TransitPoint.class));
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
        assertThat(response.markers().get(0).arriveAt()).isEqualTo(LocalTime.parse("09:11"));
        assertThat(response.markers().get(0).departAt()).isEqualTo(LocalTime.parse("10:11"));
        assertThat(response.markers().get(0).subtitle()).isEqualTo("관광지 · 체류 60분");
        assertThat(response.markers().get(0).riskLevel()).isEqualTo("NORMAL");
        assertThat(response.routeLines()).hasSize(4);
        assertThat(response.routeLines().get(0).routeOrder()).isEqualTo(1);
        assertThat(response.routeLines().get(0).fallbackUsed()).isFalse();
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

    @Test
    @DisplayName("일정 수정에서 하루 5곳을 초과하면 거부한다")
    void updateRejectsMoreThanFiveStopsPerDay() {
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule();
        new ScheduleDay(schedule, 1, LocalDate.parse("2026-06-23"));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        ScheduleUpdateRequest request = new ScheduleUpdateRequest(List.of(
                new ScheduleUpdateRequest.Stop(null, 1L, 1, 1, 60),
                new ScheduleUpdateRequest.Stop(null, 2L, 1, 2, 60),
                new ScheduleUpdateRequest.Stop(null, 3L, 1, 3, 60),
                new ScheduleUpdateRequest.Stop(null, 4L, 1, 4, 60),
                new ScheduleUpdateRequest.Stop(null, 5L, 1, 5, 60),
                new ScheduleUpdateRequest.Stop(null, 6L, 1, 6, 60)
        ));

        assertThatThrownBy(() -> scheduleService.update(scheduleId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_CONDITION);
        verify(placeRepository, never()).findAllById(Mockito.any());
        verify(transitRouteProvider, never()).findRoute(Mockito.any(), Mockito.any());
    }

    private ScheduleService service(AiSchedulePlanGenerator aiSchedulePlanGenerator) {
        PlacePreferenceScorer preferenceScorer = new PlacePreferenceScorer();
        return new ScheduleService(
                scheduleRepository,
                placeRepository,
                transitRouteProvider,
                new ScheduleRequestValidator(),
                new DayRouteOptimizer(),
                new SchedulePersistenceService(scheduleRepository),
                new ScheduleHardGateEvaluator(),
                new ScheduleScoreEvaluator(),
                new MultiDayPlanOptimizer(new DayPlaceAllocator(), preferenceScorer),
                new ScheduleFeasibilityChecker(),
                preferenceScorer,
                new PlaceCandidateProvider(placeRepository, preferenceScorer),
                new ExternalCallMetricsCollector(),
                new FixedEventPlanner(),
                new PlannerRouteEstimator(),
                SchedulePlannerProperties.defaults(),
                aiSchedulePlanGenerator
        );
    }

    private AiSchedulePlanGenerator disabledAiGenerator() {
        return new AiSchedulePlanGenerator(
                request -> {
                    throw new AssertionError("비활성 AI 클라이언트는 호출되면 안 됩니다.");
                },
                new OpenAiPlanningProperties(false, null, "", null, null, null)
        );
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

    private ScheduleCreateRequest oneDayRequest(List<Long> mustVisitPlaceIds) {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-23"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS")),
                mustVisitPlaceIds
        );
    }

    private ScheduleCreateRequest packedOneDayRequest() {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-23"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                List.of(
                        new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS"),
                        new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_PACKED")
                ),
                List.of()
        );
    }

    private ScheduleCreateRequest threeDayRequest(List<Long> mustVisitPlaceIds) {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-25"),
                LocalTime.parse("09:00"),
                LocalTime.parse("19:00"),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                List.of(new ScheduleCreateRequest.SelectedAnswer("COMPANION", "COMPANION_PARENTS")),
                mustVisitPlaceIds
        );
    }

    private ScheduleCreateRequest twoHourRequest(
            List<ScheduleCreateRequest.SelectedAnswer> selectedAnswers,
            List<Long> mustVisitPlaceIds
    ) {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-06-23"),
                LocalDate.parse("2026-06-23"),
                LocalTime.parse("09:00"),
                LocalTime.parse("11:00"),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                selectedAnswers,
                mustVisitPlaceIds
        );
    }

    private ScheduleCreateRequest oneDayScenarioRequest(List<ScheduleCreateRequest.SelectedAnswer> selectedAnswers) {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-07-07"),
                LocalDate.parse("2026-07-07"),
                LocalTime.parse("09:00"),
                LocalTime.parse("18:00"),
                new ScheduleCreateRequest.Location("부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151")),
                new ScheduleCreateRequest.Location("김해국제공항", new BigDecimal("128.948489"), new BigDecimal("35.173220")),
                selectedAnswers,
                List.of()
        );
    }

    private ScheduleCreateRequest.Location location(String name, String longitude, String latitude) {
        return new ScheduleCreateRequest.Location(name, new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private ScheduleCreateRequest.DayCondition dayCondition(
            int dayNo,
            String startTime,
            String endTime,
            ScheduleCreateRequest.Location startLocation,
            ScheduleCreateRequest.Location endLocation
    ) {
        return new ScheduleCreateRequest.DayCondition(
                dayNo,
                LocalTime.parse(startTime),
                LocalTime.parse(endTime),
                startLocation,
                endLocation
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
        return place(id, name, "12", longitude, latitude);
    }

    private Place place(Long id, String name, String contentTypeId, String longitude, String latitude) {
        Place place = new Place(
                "TOUR_API",
                "content-" + id,
                contentTypeId,
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
        return route(25);
    }

    private TransitRouteResult route(int totalMinutes) {
        return new TransitRouteResult(
                totalMinutes,
                1550,
                List.of(new TransitRouteResult.Segment("BUS", "26", "부산역", "남부민2동")),
                List.of(new TransitRouteResult.RouteLine("BUS", "26", "[[129.0,35.0],[129.1,35.1]]")),
                "{\"provider\":\"FAKE\"}"
        );
    }

}
