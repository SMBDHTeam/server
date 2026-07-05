package com.server.transit.service;

import java.math.BigDecimal;

public record TransitPoint(
        String name,
        BigDecimal longitude,
        BigDecimal latitude
) {
}
