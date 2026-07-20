package com.server.schedule.planner;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.schedule.dto.ScheduleCreateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyScheduleTargetPolicyTest {

    @Test
    void packedDayTargetsFiveItemsOnlyWhenThereIsEnoughTime() {
        List<ScheduleCreateRequest.SelectedAnswer> answers = List.of(
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_PACKED"));

        assertThat(DailyScheduleTargetPolicy.target(480, answers)).isEqualTo(5);
        assertThat(DailyScheduleTargetPolicy.target(360, answers)).isEqualTo(4);
        assertThat(DailyScheduleTargetPolicy.target(300, answers)).isEqualTo(3);
        assertThat(DailyScheduleTargetPolicy.target(120, answers)).isEqualTo(1);
    }

    @Test
    void relaxedDayUsesThreeItemsAsASoftTargetOnlyAfterSixHours() {
        List<ScheduleCreateRequest.SelectedAnswer> answers = List.of(
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_RELAXED"));

        assertThat(DailyScheduleTargetPolicy.target(360, answers)).isEqualTo(3);
        assertThat(DailyScheduleTargetPolicy.target(300, answers)).isEqualTo(3);
        assertThat(DailyScheduleTargetPolicy.target(120, answers)).isEqualTo(1);
    }

    @Test
    void balancedDayTargetsFourItemsAfterEightHours() {
        assertThat(DailyScheduleTargetPolicy.target(480, List.of())).isEqualTo(4);
        assertThat(DailyScheduleTargetPolicy.target(360, List.of())).isEqualTo(4);
    }

    @Test
    void exposesAbsolutePreferredTargetAndMaximumCounts() {
        PlaceCountPolicy relaxed = DailyScheduleTargetPolicy.policy(600, List.of(
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_RELAXED")));
        PlaceCountPolicy balanced = DailyScheduleTargetPolicy.policy(600, List.of());
        PlaceCountPolicy packed = DailyScheduleTargetPolicy.policy(600, List.of(
                new ScheduleCreateRequest.SelectedAnswer("PACE", "PACE_PACKED")));

        assertThat(relaxed).isEqualTo(new PlaceCountPolicy(2, 3, 3, 4, 55));
        assertThat(balanced).isEqualTo(new PlaceCountPolicy(2, 3, 4, 5, 70));
        assertThat(packed).isEqualTo(new PlaceCountPolicy(2, 4, 5, 5, 85));
        assertThat(relaxed.targetActivityMinutes(600)).isEqualTo(270);
    }
}
