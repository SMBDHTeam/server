package com.server.schedule.planner;

/** Central factory for the multi-day objective components. */
public final class PlanObjectiveEvaluator {

    private PlanObjectiveEvaluator() {
    }

    public static PlanObjective evaluate(
            int confirmedHardViolations,
            double feasibilityRisk,
            double fatigueCost,
            double routeFlowCost,
            double rhythmCost,
            double preferenceCost,
            double diversityCost,
            double placeCountCost
    ) {
        return new PlanObjective(
                confirmedHardViolations,
                feasibilityRisk,
                fatigueCost,
                routeFlowCost,
                rhythmCost,
                preferenceCost,
                diversityCost,
                placeCountCost
        );
    }
}
