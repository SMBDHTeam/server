package com.server.schedule.planner;

/** Lexicographic plan-quality objective for multi-day plan comparison. */
public record PlanObjective(
        int confirmedHardViolations,
        double feasibilityRisk,
        double fatigueCost,
        double routeFlowCost,
        double rhythmCost,
        double preferenceCost,
        double diversityCost,
        double placeCountCost
) implements Comparable<PlanObjective> {

    @Override
    public int compareTo(PlanObjective other) {
        int comparison = Integer.compare(confirmedHardViolations, other.confirmedHardViolations);
        if (comparison != 0) return comparison;
        comparison = Double.compare(feasibilityRisk, other.feasibilityRisk);
        if (comparison != 0) return comparison;
        comparison = Double.compare(fatigueCost, other.fatigueCost);
        if (comparison != 0) return comparison;
        comparison = Double.compare(routeFlowCost, other.routeFlowCost);
        if (comparison != 0) return comparison;
        comparison = Double.compare(rhythmCost, other.rhythmCost);
        if (comparison != 0) return comparison;
        comparison = Double.compare(preferenceCost, other.preferenceCost);
        if (comparison != 0) return comparison;
        comparison = Double.compare(diversityCost, other.diversityCost);
        if (comparison != 0) return comparison;
        return Double.compare(placeCountCost, other.placeCountCost);
    }
}
