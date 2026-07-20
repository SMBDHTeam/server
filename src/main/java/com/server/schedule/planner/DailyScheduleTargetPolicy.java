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
        return policy(availableMinutes, answers).targetCount();
    }

    public static PlaceCountPolicy policy(
            long availableMinutes,
            List<ScheduleCreateRequest.SelectedAnswer> answers
    ) {
        if (availableMinutes <= 120) {
            return new PlaceCountPolicy(1, 1, 1, 1, 50);
        }
        if (availableMinutes < 240) {
            return new PlaceCountPolicy(1, 1, 2, 2, 50);
        }
        if (hasAnswer(answers, "PACE_PACKED")) {
            if (availableMinutes >= EIGHT_HOURS) {
                return new PlaceCountPolicy(2, 4, 5, 5, 85);
            }
            if (availableMinutes >= SIX_HOURS) {
                return new PlaceCountPolicy(2, 3, 4, 5, 85);
            }
            return new PlaceCountPolicy(2, 2, 3, 3, 85);
        }
        if (hasAnswer(answers, "PACE_RELAXED")) {
            if (availableMinutes >= SIX_HOURS) {
                return new PlaceCountPolicy(2, 3, 3, 4, 55);
            }
            return new PlaceCountPolicy(2, 2, 3, 3, 55);
        }
        if (availableMinutes >= EIGHT_HOURS) {
            return new PlaceCountPolicy(2, 3, 4, 5, 70);
        }
        if (availableMinutes >= SIX_HOURS) {
            return new PlaceCountPolicy(2, 3, 4, 4, 70);
        }
        return new PlaceCountPolicy(2, 2, 3, 3, 70);
    }

    private static boolean hasAnswer(
            List<ScheduleCreateRequest.SelectedAnswer> answers,
            String answerId
    ) {
        return answers.stream().anyMatch(answer -> answerId.equals(answer.answerId()));
    }
}
