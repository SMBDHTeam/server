package com.server.transit.service;

import java.util.Optional;

public interface WalkingRouteProvider {

    Optional<WalkingRouteResult> findRoute(TransitPoint origin, TransitPoint destination);
}
