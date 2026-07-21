package com.server.schedule.planner;

/** Travel-pace policy for the number of all itinerary items in one day. */
public record PlaceCountPolicy(
        int absoluteMinimum,
        int preferredMinimum,
        int targetCount,
        int maximum,
        int targetUtilizationPercent
) {

    private static final int UTILIZATION_BUFFER_MINUTES = 60;
    private static final int PREFERRED_MINIMUM_PENALTY = 10_000;
    private static final int TARGET_DEVIATION_PENALTY = 1_000;

    public PlaceCountPolicy {
        if (absoluteMinimum < 1
                || absoluteMinimum > preferredMinimum
                || preferredMinimum > targetCount
                || targetCount > maximum
                || maximum > DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY) {
            throw new IllegalArgumentException("Invalid daily place count policy");
        }
        if (targetUtilizationPercent < 0 || targetUtilizationPercent > 100) {
            throw new IllegalArgumentException("Invalid target utilization percent");
        }
    }

    public static PlaceCountPolicy exact(int count) {
        return new PlaceCountPolicy(count, count, count, count, 0);
    }

    public PlaceCountPolicy withRequiredCount(int requiredCount) {
        if (requiredCount > DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY) {
            throw new IllegalArgumentException("Required place count exceeds the daily maximum");
        }
        return new PlaceCountPolicy(
                Math.max(absoluteMinimum, requiredCount),
                Math.max(preferredMinimum, requiredCount),
                Math.max(targetCount, requiredCount),
                Math.max(maximum, requiredCount),
                targetUtilizationPercent
        );
    }

    public PlaceCountPolicy limitToAvailableCandidates(int availableCount) {
        if (availableCount < 1) {
            throw new IllegalArgumentException("At least one daily candidate is required");
        }
        int limitedTarget = Math.min(targetCount, availableCount);
        return new PlaceCountPolicy(
                Math.min(absoluteMinimum, limitedTarget),
                Math.min(preferredMinimum, limitedTarget),
                limitedTarget,
                limitedTarget,
                targetUtilizationPercent
        );
    }

    public int targetActivityMinutes(long availableMinutes) {
        int targetMinutes = (int) Math.ceil(availableMinutes * targetUtilizationPercent / 100.0);
        return Math.max(0, targetMinutes - UTILIZATION_BUFFER_MINUTES);
    }

    public int placeCountCost(int count) {
        if (count < absoluteMinimum || count > maximum) {
            return Integer.MAX_VALUE;
        }
        int preferredShortfall = Math.max(0, preferredMinimum - count);
        return preferredShortfall * PREFERRED_MINIMUM_PENALTY
                + Math.abs(targetCount - count) * TARGET_DEVIATION_PENALTY;
    }
}
