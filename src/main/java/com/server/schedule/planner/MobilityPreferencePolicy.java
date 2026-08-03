package com.server.schedule.planner;

import com.server.schedule.dto.ScheduleCreateRequest;
import java.util.List;

/**
 * Single source of truth for how mobility related answers are interpreted.
 *
 * <p>Low walk preference is not binary. Explicitly chosen answers such as travelling with
 * parents state a hard mobility limit, while a free prompt only hints that the traveller
 * would rather walk less. Search, feasibility and scoring all have to agree on that
 * distinction, otherwise the planner optimizes for one bar and validates against another.
 */
public final class MobilityPreferencePolicy {

    private static final List<String> STRONG_LOW_WALK_ANSWER_IDS = List.of(
            "COMPANION_PARENTS",
            "COMPANION_FAMILY_WITH_CHILD",
            "MOBILITY_LOW_WALK",
            "MOBILITY_AVOID_HILLS_STAIRS"
    );
    private static final String MODERATE_LOW_WALK_ANSWER_ID = "PROMPT_LOW_WALKING";
    private static final String SIMPLE_TRANSIT_ANSWER_ID = "TRANSIT_SIMPLE";

    private MobilityPreferencePolicy() {
    }

    public static LowWalkLevel lowWalkLevel(ScheduleCreateRequest request) {
        if (STRONG_LOW_WALK_ANSWER_IDS.stream().anyMatch(answerId -> hasAnswer(request, answerId))) {
            return LowWalkLevel.STRONG;
        }
        if (hasAnswer(request, MODERATE_LOW_WALK_ANSWER_ID)) {
            return LowWalkLevel.MODERATE;
        }
        return LowWalkLevel.NONE;
    }

    public static boolean prefersSimpleTransit(ScheduleCreateRequest request) {
        return hasAnswer(request, SIMPLE_TRANSIT_ANSWER_ID);
    }

    private static boolean hasAnswer(ScheduleCreateRequest request, String answerId) {
        if (request == null || request.selectedAnswers() == null) return false;
        return request.selectedAnswers().stream()
                .anyMatch(answer -> answer != null && answerId.equals(answer.answerId()));
    }

    public enum LowWalkLevel {

        /** No stated walking limit. Only the generic per-leg guard applies. */
        NONE(0),
        /** Free prompt hint. Walking is discouraged but not treated as a hard limit. */
        MODERATE(1),
        /** Explicitly chosen mobility limit. Walking is penalised hardest. */
        STRONG(2);

        private final int walkPenaltyMultiplier;

        LowWalkLevel(int walkPenaltyMultiplier) {
            this.walkPenaltyMultiplier = walkPenaltyMultiplier;
        }

        public int walkPenaltyMultiplier() {
            return walkPenaltyMultiplier;
        }

        public boolean isStrong() {
            return this == STRONG;
        }

        public boolean isAtLeastModerate() {
            return this != NONE;
        }
    }
}
