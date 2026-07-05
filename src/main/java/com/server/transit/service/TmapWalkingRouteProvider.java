package com.server.transit.service;

import com.server.external.tmap.TmapWalkingClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.tmap.walking", name = "enabled", havingValue = "true")
public class TmapWalkingRouteProvider implements WalkingRouteProvider {

    private final TmapWalkingClient tmapWalkingClient;

    public TmapWalkingRouteProvider(TmapWalkingClient tmapWalkingClient) {
        this.tmapWalkingClient = tmapWalkingClient;
    }

    @Override
    public Optional<WalkingRouteResult> findRoute(TransitPoint origin, TransitPoint destination) {
        return tmapWalkingClient.findWalkingRoute(
                        origin.longitude(),
                        origin.latitude(),
                        destination.longitude(),
                        destination.latitude()
                )
                .map(route -> new WalkingRouteResult(
                        (int) Math.ceil(route.totalSeconds() / 60.0),
                        route.distanceMeters(),
                        jsonValue(route.coordinates())
                ));
    }

    private String jsonValue(List<List<BigDecimal>> coordinates) {
        return "[" + String.join(",", coordinates.stream()
                .map(coordinate -> "[" + coordinate.get(0).toPlainString() + "," + coordinate.get(1).toPlainString() + "]")
                .toList()) + "]";
    }
}
