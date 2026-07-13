package com.server.transit.service;

public record TransitRealtimeAdjustment(
        int extraMinutes,
        String status,
        String detail
) {

    public static TransitRealtimeAdjustment none() {
        return new TransitRealtimeAdjustment(0, "UNAVAILABLE", null);
    }

    public boolean hasPenalty() {
        return extraMinutes > 0;
    }
}
