package com.server.transit.service;

public interface TransitRealtimeProvider {

    TransitRealtimeAdjustment adjustment(TransitRealtimeRequest request);
}
