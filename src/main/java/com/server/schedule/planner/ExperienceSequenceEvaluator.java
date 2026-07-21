package com.server.schedule.planner;

import com.server.place.domain.Place;
import java.util.List;

/** Evaluates repeated experiences without treating food and rest stops as repeated attractions. */
final class ExperienceSequenceEvaluator {

    private static final int CONSECUTIVE_SAME_EXPERIENCE_PENALTY = 12;
    private static final int CONSECUTIVE_SAME_SEMANTIC_GROUP_PENALTY = 4;

    private ExperienceSequenceEvaluator() {
    }

    static int consecutivePenalty(List<Place> order) {
        int penalty = 0;
        for (int index = 1; index < order.size(); index++) {
            PlaceExperienceClassifier.ExperienceProfile previous =
                    PlaceExperienceClassifier.classify(order.get(index - 1));
            PlaceExperienceClassifier.ExperienceProfile current =
                    PlaceExperienceClassifier.classify(order.get(index));
            if (!isDiversityScored(previous) || !isDiversityScored(current)) continue;
            if (previous.type() == current.type()) {
                penalty += CONSECUTIVE_SAME_EXPERIENCE_PENALTY;
            }
            if (previous.semanticGroup() == current.semanticGroup()) {
                penalty += CONSECUTIVE_SAME_SEMANTIC_GROUP_PENALTY;
            }
        }
        return penalty;
    }

    static boolean isDiversityScored(PlaceExperienceClassifier.ExperienceProfile profile) {
        return profile.type() != PlaceExperienceClassifier.ExperienceType.OTHER
                && profile.semanticGroup() != PlaceExperienceClassifier.SemanticGroup.OTHER
                && profile.semanticGroup() != PlaceExperienceClassifier.SemanticGroup.FOOD_REST;
    }
}
