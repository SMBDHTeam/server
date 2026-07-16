package com.server.schedule.planner;

import com.server.place.domain.Place;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.domain.ScheduleStop;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MealTimePolicy {

    private static final int MINIMUM_WINDOW_OVERLAP_MINUTES = 45;

    private MealTimePolicy() {
    }

    public static boolean isMealPlace(Place place) {
        if (place == null) return false;
        if ("39".equals(place.getContentTypeId())) return true;
        String category = normalize(place.getCategory());
        String name = normalize(place.getName());
        return category.startsWith("a0502")
                || containsAny(category, "음식", "식당", "카페", "베이커리")
                || containsAny(name, "맛집", "식당", "카페", "커피", "베이커리");
    }

    public static List<MealSlot> activeSlots(ScheduleDay day) {
        List<MealSlot> slots = new ArrayList<>();
        for (MealSlot slot : MealSlot.values()) {
            LocalTime overlapStart = day.getStartTime().isAfter(slot.start())
                    ? day.getStartTime() : slot.start();
            LocalTime overlapEnd = day.getEndTime().isBefore(slot.end())
                    ? day.getEndTime() : slot.end();
            if (overlapEnd.isAfter(overlapStart)
                    && Duration.between(overlapStart, overlapEnd).toMinutes()
                            >= MINIMUM_WINDOW_OVERLAP_MINUTES) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    public static int requiredMealStops(ScheduleDay day, int stopTarget) {
        if (stopTarget <= 2 || activeSlots(day).isEmpty()) return 0;
        if (stopTarget >= DailyScheduleTargetPolicy.MAX_STOPS_PER_DAY
                && activeSlots(day).size() >= 2) {
            return 2;
        }
        return 1;
    }

    public static Alignment alignArrival(
            LocalTime arrival,
            Place place,
            List<MealSlot> activeSlots,
            Set<MealSlot> assignedSlots
    ) {
        if (!isMealPlace(place)) return new Alignment(arrival, null, 0);
        for (MealSlot slot : activeSlots) {
            if (assignedSlots.contains(slot) || arrival.isAfter(slot.end())) continue;
            LocalTime aligned = arrival.isBefore(slot.start()) ? slot.start() : arrival;
            int waitingMinutes = (int) Duration.between(arrival, aligned).toMinutes();
            return new Alignment(aligned, slot, waitingMinutes);
        }
        return new Alignment(arrival, null, 0);
    }

    public static long waitingMinutes(ScheduleDay day) {
        LocalTime cursor = day.getStartTime();
        List<MealSlot> activeSlots = activeSlots(day);
        Set<MealSlot> assignedSlots = EnumSet.noneOf(MealSlot.class);
        long waitingMinutes = 0;
        for (ScheduleStop stop : day.getStops()) {
            if (stop.getInboundTransit() != null) {
                cursor = cursor.plusMinutes(stop.getInboundTransit().getTotalMinutes());
            }
            if (stop.getFixedStartsAt() != null) {
                LocalTime fixedStart = stop.getFixedStartsAt()
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalTime();
                if (cursor.isBefore(fixedStart)) {
                    waitingMinutes += Duration.between(cursor, fixedStart).toMinutes();
                }
                Alignment alignment = alignArrival(
                        fixedStart, stop.getPlace(), activeSlots, assignedSlots);
                if (alignment.slot() != null) assignedSlots.add(alignment.slot());
                cursor = stop.getFixedEndsAt()
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalTime();
                continue;
            }
            Alignment alignment = alignArrival(cursor, stop.getPlace(), activeSlots, assignedSlots);
            cursor = alignment.arrival();
            waitingMinutes += alignment.waitingMinutes();
            if (alignment.slot() != null) assignedSlots.add(alignment.slot());
            cursor = cursor.plusMinutes(stop.getStayMinutes());
        }
        return waitingMinutes;
    }

    public static int orderPenalty(ScheduleDay day, List<Place> places) {
        List<MealSlot> slots = activeSlots(day);
        if (slots.size() < 2) return 0;
        List<Integer> mealIndexes = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            if (isMealPlace(places.get(index))) mealIndexes.add(index);
        }
        if (mealIndexes.size() < 2) return 0;

        int penalty = Math.abs(mealIndexes.get(mealIndexes.size() - 1) - (places.size() - 1)) * 180;
        if (places.size() > 2 && day.getStartTime().isBefore(LocalTime.of(10, 30))) {
            penalty += Math.abs(mealIndexes.get(0) - 1) * 90;
        }
        return penalty;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    public enum MealSlot {
        LUNCH(LocalTime.of(11, 0), LocalTime.of(14, 0)),
        DINNER(LocalTime.of(17, 0), LocalTime.of(19, 0));

        private final LocalTime start;
        private final LocalTime end;

        MealSlot(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalTime start() { return start; }
        public LocalTime end() { return end; }
    }

    public record Alignment(
            LocalTime arrival,
            MealSlot slot,
            int waitingMinutes
    ) {
    }
}
