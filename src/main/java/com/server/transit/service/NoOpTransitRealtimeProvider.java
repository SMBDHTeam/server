package com.server.transit.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.busan-bims", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpTransitRealtimeProvider implements TransitRealtimeProvider {

    @Override
    public TransitRealtimeAdjustment adjustment(TransitRealtimeRequest request) {
        return TransitRealtimeAdjustment.none();
    }
}
