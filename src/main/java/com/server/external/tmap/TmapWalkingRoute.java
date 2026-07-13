package com.server.external.tmap;

import java.math.BigDecimal;
import java.util.List;

public record TmapWalkingRoute(
        int totalSeconds,
        Integer distanceMeters,
        List<List<BigDecimal>> coordinates
) {
}
