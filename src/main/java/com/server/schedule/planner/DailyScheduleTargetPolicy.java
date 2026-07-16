package com.server.schedule.planner;

import com.server.schedule.dto.ScheduleCreateRequest;
import java.util.List;

/**
 * Chooses a target number of all daily itinerary items, including meals.
 * The target is a quality preference; feasibility checks may remove optional stops.
 */
public final class DailyScheduleTargetPolicy {

    public static final int MAX_STOPS_PER_DAY = 5;
    private static final int SIX_HOURS = 360;
    private static final int EIGHT_HOURS = 480;

    private DailyScheduleTargetPolicy() {
    }

    public static int target(long availableMinutes, List<ScheduleCreateRequest.SelectedAnswer> answers) {
        if (hasAnswer(answers, "PACE_PACKED")) {
            if (availableMinutes >= EIGHT_HOURS) return 5;
            if (availableMinutes >= SIX_HOURS) return 4;
            return availableMinutes >= 240 ? 3 : 1;
        }
        if (hasAnswer(answers, "PACE_RELAXED")) {
            if (availableMinutes >= SIX_HOURS) return 3;
            return availableMinutes >= 240 ? 2 : 1;
        }
        if (availableMinutes >= EIGHT_HOURS) return 4;
        if (availableMinutes >= SIX_HOURS) return 3;
        return availableMinutes >= 240 ? 2 : 1;
    }

    private static boolean hasAnswer(
            List<ScheduleCreateRequest.SelectedAnswer> answers,
            String answerId
    ) {
        return answers.stream().anyMatch(answer -> answerId.equals(answer.answerId()));
    }
}
