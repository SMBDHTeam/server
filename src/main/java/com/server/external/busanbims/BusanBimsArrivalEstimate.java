package com.server.external.busanbims;

public record BusanBimsArrivalEstimate(
        int waitMinutes,
        String routeName,
        String status
) {
}
