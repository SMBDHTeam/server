package com.server.transit.service;

public interface TransitRouteProvider {

    TransitRouteResult findRoute(TransitPoint origin, TransitPoint destination);

    default TransitRouteEstimate findRouteEstimate(TransitPoint origin, TransitPoint destination) {
        return TransitRouteEstimate.detailed(findRoute(origin, destination));
    }

    default TransitRouteResult findRouteDetail(
            TransitPoint origin,
            TransitPoint destination,
            TransitRouteEstimate estimate
    ) {
        if (estimate != null && !estimate.requiresDetail()) {
            return estimate.route();
        }
        return findRoute(origin, destination);
    }
}
