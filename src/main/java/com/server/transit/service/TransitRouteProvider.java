package com.server.transit.service;

public interface TransitRouteProvider {

    TransitRouteResult findRoute(TransitPoint origin, TransitPoint destination);
}
