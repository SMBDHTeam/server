package com.server.transit.service;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.tmap.walking", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpWalkingRouteProvider implements WalkingRouteProvider {

    @Override
    public Optional<WalkingRouteResult> findRoute(TransitPoint origin, TransitPoint destination) {
        return Optional.empty();
    }
}
