package com.server.transit.service;

import com.server.external.busanbims.BusanBimsArrivalEstimate;
import com.server.external.busanbims.BusanBimsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.busan-bims", name = "enabled", havingValue = "true")
public class BusanBimsTransitRealtimeProvider implements TransitRealtimeProvider {

    private static final int EXPECTED_BASE_WAIT_MINUTES = 7;

    private final BusanBimsClient busanBimsClient;

    public BusanBimsTransitRealtimeProvider(BusanBimsClient busanBimsClient) {
        this.busanBimsClient = busanBimsClient;
    }

    @Override
    public TransitRealtimeAdjustment adjustment(TransitRealtimeRequest request) {
        if (request == null || !"BUS".equals(request.mode()) || isBlank(request.startStationId())) {
            return TransitRealtimeAdjustment.none();
        }

        return busanBimsClient.findArrival(request.startStationId(), request.lineName())
                .map(this::adjustment)
                .orElseGet(TransitRealtimeAdjustment::none);
    }

    private TransitRealtimeAdjustment adjustment(BusanBimsArrivalEstimate estimate) {
        int extraMinutes = Math.max(0, estimate.waitMinutes() - EXPECTED_BASE_WAIT_MINUTES);
        String status = extraMinutes > 0 ? estimate.status() : "NORMAL";
        String detail = "route=" + nullToBlank(estimate.routeName())
                + ",waitMinutes=" + estimate.waitMinutes();
        return new TransitRealtimeAdjustment(extraMinutes, status, detail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
