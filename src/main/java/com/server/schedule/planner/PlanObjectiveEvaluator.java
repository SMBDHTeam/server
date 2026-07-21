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

    public static PlanObjective empty() {
        return evaluate(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static PlanObjective combine(PlanObjective left, PlanObjective right) {
        return evaluate(
                left.confirmedHardViolations() + right.confirmedHardViolations(),
                left.feasibilityRisk() + right.feasibilityRisk(),
                left.fatigueCost() + right.fatigueCost(),
                left.routeFlowCost() + right.routeFlowCost(),
                left.rhythmCost() + right.rhythmCost(),
                left.preferenceCost() + right.preferenceCost(),
                left.diversityCost() + right.diversityCost(),
                left.placeCountCost() + right.placeCountCost()
        );
    }

    public static PlanObjective addDiversityCost(PlanObjective objective, double diversityCost) {
        return evaluate(
                objective.confirmedHardViolations(),
                objective.feasibilityRisk(),
                objective.fatigueCost(),
                objective.routeFlowCost(),
                objective.rhythmCost(),
                objective.preferenceCost(),
                objective.diversityCost() + diversityCost,
                objective.placeCountCost()
        );
    }
}
