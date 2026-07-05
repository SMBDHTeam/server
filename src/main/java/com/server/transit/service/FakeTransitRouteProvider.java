package com.server.transit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.odsay", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FakeTransitRouteProvider implements TransitRouteProvider {

    @Override
    public TransitRouteResult findRoute(TransitPoint origin, TransitPoint destination) {
        int minutes = Math.max(10, estimatedMinutes(origin, destination));
        String lineName = "FAKE-BUS";
        String coordinatesJson = "[["
                + origin.longitude() + "," + origin.latitude()
                + "],["
                + destination.longitude() + "," + destination.latitude()
                + "]]";
        return new TransitRouteResult(
                minutes,
                1550,
                List.of(new TransitRouteResult.Segment(
                        "BUS",
                        lineName,
                        origin.name(),
                        destination.name()
                )),
                List.of(new TransitRouteResult.RouteLine(
                        "BUS",
                        lineName,
                        coordinatesJson
                )),
                "{\"provider\":\"FAKE\"}"
        );
    }

    private int estimatedMinutes(TransitPoint origin, TransitPoint destination) {
        BigDecimal deltaLongitude = origin.longitude().subtract(destination.longitude()).abs();
        BigDecimal deltaLatitude = origin.latitude().subtract(destination.latitude()).abs();
        BigDecimal score = deltaLongitude.add(deltaLatitude)
                .multiply(new BigDecimal("1000"))
                .setScale(0, RoundingMode.HALF_UP);
        return score.intValue();
    }
}
