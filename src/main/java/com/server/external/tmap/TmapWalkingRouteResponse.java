package com.server.external.tmap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record TmapWalkingRouteResponse(
        List<Feature> features
) {

    public List<List<BigDecimal>> lineStringCoordinates() {
        if (features == null || features.isEmpty()) {
            return List.of();
        }

        List<List<BigDecimal>> coordinates = new ArrayList<>();
        for (Feature feature : features) {
            if (feature.geometry() == null || !"LineString".equals(feature.geometry().type())) {
                continue;
            }
            appendLineStringCoordinates(coordinates, feature.geometry().coordinates());
        }
        return coordinates;
    }

    public Integer totalSeconds() {
        return features == null ? null : features.stream()
                .map(Feature::properties)
                .filter(properties -> properties != null && properties.totalTime() != null)
                .map(Properties::totalTime)
                .findFirst()
                .orElse(null);
    }

    public Integer distanceMeters() {
        return features == null ? null : features.stream()
                .map(Feature::properties)
                .filter(properties -> properties != null && properties.totalDistance() != null)
                .map(Properties::totalDistance)
                .findFirst()
                .orElse(null);
    }

    private void appendLineStringCoordinates(List<List<BigDecimal>> coordinates, Object value) {
        if (!(value instanceof List<?> lineString)) {
            return;
        }

        for (Object item : lineString) {
            List<BigDecimal> coordinate = coordinate(item);
            if (coordinate == null) {
                continue;
            }
            if (!coordinates.isEmpty() && sameCoordinate(coordinates.get(coordinates.size() - 1), coordinate)) {
                continue;
            }
            coordinates.add(coordinate);
        }
    }

    private List<BigDecimal> coordinate(Object value) {
        if (!(value instanceof List<?> pair) || pair.size() < 2) {
            return null;
        }
        return List.of(decimal(pair.get(0)), decimal(pair.get(1)));
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private boolean sameCoordinate(List<BigDecimal> first, List<BigDecimal> second) {
        return first.get(0).compareTo(second.get(0)) == 0
                && first.get(1).compareTo(second.get(1)) == 0;
    }

    public record Feature(
            Geometry geometry,
            Properties properties
    ) {
    }

    public record Geometry(
            String type,
            Object coordinates
    ) {
    }

    public record Properties(
            Integer totalTime,
            Integer totalDistance
    ) {
    }
}
