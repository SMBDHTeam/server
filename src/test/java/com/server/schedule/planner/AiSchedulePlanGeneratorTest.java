package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.external.openai.AiScheduleProposalClient;
import com.server.external.openai.OpenAiPlanningProperties;
import com.server.place.domain.Place;
import com.server.schedule.domain.Schedule;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiSchedulePlanGeneratorTest {

    @Test
    void acceptsAValidConstraintCompleteProposal() {
        Place attraction = place(101L, "광안리", "12", "관광지");
        Place restaurant = place(102L, "광안리 식당", "39", "음식점");
        AiScheduleProposalClient client = request -> new AiScheduleProposalClient.Proposal(
                List.of(new AiScheduleProposalClient.DayProposal(1, List.of(101L, 102L))),
                91,
                "필수 장소와 점심을 연결"
        );
        AiSchedulePlanGenerator generator = generator(client, true);
        ScheduleDay day = day("11:00", "16:00");

        var result = generator.generate(
                List.of(attraction, restaurant), Set.of(101L), List.of(day), List.of(2),
                request(), Map.of(), "바다를 보고 식사");

        assertThat(result.source()).isEqualTo("AI_PROPOSED");
        assertThat(result.confidence()).isEqualTo(91);
        assertThat(result.placesByDay().get(0)).containsExactly(attraction, restaurant);
    }

    @Test
    void rejectsProposalThatMissesRequiredOrMealPlaces() {
        Place attraction = place(101L, "필수 관광지", "12", "관광지");
        Place another = place(103L, "다른 관광지", "12", "관광지");
        AiScheduleProposalClient client = request -> new AiScheduleProposalClient.Proposal(
                List.of(new AiScheduleProposalClient.DayProposal(1, List.of(103L))),
                99,
                "제약을 누락한 응답"
        );
        AiSchedulePlanGenerator generator = generator(client, true);

        var result = generator.generate(
                List.of(attraction, another), Set.of(101L), List.of(day("11:00", "14:00")),
                List.of(1), request(), Map.of(), null);

        assertThat(result.source()).isEqualTo("AI_FALLBACK");
        assertThat(result.hasProposal()).isFalse();
    }

    @Test
    void disabledPlannerDoesNotCallAi() {
        AtomicBoolean called = new AtomicBoolean();
        AiSchedulePlanGenerator generator = generator(request -> {
            called.set(true);
            throw new AssertionError("must not call AI");
        }, false);

        var result = generator.generate(
                List.of(place(101L, "장소", "12", "관광지")), Set.of(),
                List.of(day("09:00", "11:00")), List.of(1), request(), Map.of(), null);

        assertThat(result.source()).isEqualTo("RULE_BASED");
        assertThat(called).isFalse();
    }

    @Test
    void enabledPlannerWithoutApiKeyReportsAiFallback() {
        AiSchedulePlanGenerator generator = new AiSchedulePlanGenerator(
                request -> {
                    throw new AssertionError("must not call AI without a key");
                },
                new OpenAiPlanningProperties(
                        true, "http://localhost", "", "test-model", null, null)
        );

        var result = generator.generate(
                List.of(place(101L, "장소", "12", "관광지")), Set.of(),
                List.of(day("09:00", "11:00")), List.of(1), request(), Map.of(), null);

        assertThat(result.source()).isEqualTo("AI_FALLBACK");
    }

    private AiSchedulePlanGenerator generator(AiScheduleProposalClient client, boolean enabled) {
        return new AiSchedulePlanGenerator(client, new OpenAiPlanningProperties(
                enabled, "http://localhost", "test-key", "test-model", null, null));
    }

    private ScheduleDay day(String start, String end) {
        Schedule schedule = new Schedule(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-20"),
                LocalTime.parse(start), LocalTime.parse(end),
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"),
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"),
                "테스트", "{}");
        return new ScheduleDay(schedule, 1, schedule.getStartDate());
    }

    private ScheduleCreateRequest request() {
        return new ScheduleCreateRequest(
                LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-20"),
                LocalTime.parse("11:00"), LocalTime.parse("16:00"),
                location(), location(),
                List.of(new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_RELAXED")),
                List.of());
    }

    private ScheduleCreateRequest.Location location() {
        return new ScheduleCreateRequest.Location(
                "부산역", new BigDecimal("129.0403"), new BigDecimal("35.1151"));
    }

    private Place place(Long id, String name, String contentTypeId, String category) {
        Place place = new Place(
                "TEST", id.toString(), contentTypeId, name, category, "부산",
                new BigDecimal("129.1000"), new BigDecimal("35.1500"), null);
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }
}
