package com.server.transit.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.odsay.OdsayClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.odsay", name = "enabled", havingValue = "true")
public class OdsayTransitRouteProvider implements TransitRouteProvider {

    private static final int TRAFFIC_TYPE_SUBWAY = 1;
    private static final int TRAFFIC_TYPE_BUS = 2;
    private static final int TRAFFIC_TYPE_WALK = 3;
    private static final Pattern COORDINATE_PAIR_PATTERN = Pattern.compile(
            "\\[\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?\\s*,\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?\\s*]"
    );

    private final OdsayClient odsayClient;
    private final WalkingRouteProvider walkingRouteProvider;

    public OdsayTransitRouteProvider(OdsayClient odsayClient, WalkingRouteProvider walkingRouteProvider) {
        this.odsayClient = odsayClient;
        this.walkingRouteProvider = walkingRouteProvider;
    }

    @Override
    public TransitRouteResult findRoute(TransitPoint origin, TransitPoint destination) {
        Map<String, Object> response = odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        );
        Map<String, Object> path = bestPath(response);
        List<Map<String, Object>> subPaths = list(path, "subPath");
        if (subPaths.isEmpty()) {
            throw new BusinessException(ErrorCode.TRANSIT_ROUTE_NOT_FOUND);
        }

        return new TransitRouteResult(
                intValue(map(path, "info"), "totalTime"),
                integerValue(map(path, "info"), "payment"),
                subPaths.stream()
                        .map(this::toSegment)
                        .toList(),
                routeLines(path, subPaths, origin, destination),
                rawJson(path)
        );
    }

    private Map<String, Object> bestPath(Map<String, Object> response) {
        List<Map<String, Object>> paths = list(map(response, "result"), "path");
        return paths.stream()
                .min(Comparator.comparingInt(path -> intValue(map(path, "info"), "totalTime")))
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSIT_ROUTE_NOT_FOUND));
    }

    private TransitRouteResult.Segment toSegment(Map<String, Object> subPath) {
        return new TransitRouteResult.Segment(
                mode(subPath),
                lineName(subPath),
                firstText(subPath, "startName", "startStationName"),
                firstText(subPath, "endName", "endStationName")
        );
    }

    private TransitRouteResult.RouteLine toRouteLine(Map<String, Object> subPath) {
        return new TransitRouteResult.RouteLine(
                mode(subPath),
                lineName(subPath),
                coordinatesJson(subPath)
        );
    }

    private List<TransitRouteResult.RouteLine> routeLines(
            Map<String, Object> path,
            List<Map<String, Object>> subPaths,
            TransitPoint origin,
            TransitPoint destination
    ) {
        List<TransitRouteResult.RouteLine> transitRouteLines = detailedRouteLines(path, subPaths);
        if (transitRouteLines.isEmpty()) {
            transitRouteLines = subPaths.stream()
                    .filter(subPath -> intValue(subPath, "trafficType") != TRAFFIC_TYPE_WALK)
                    .map(this::toRouteLine)
                    .filter(routeLine -> routeLine.coordinatesJson() != null)
                    .toList();
        }
        return routeLinesWithWalk(subPaths, transitRouteLines, origin, destination);
    }

    private List<TransitRouteResult.RouteLine> routeLinesWithWalk(
            List<Map<String, Object>> subPaths,
            List<TransitRouteResult.RouteLine> transitRouteLines,
            TransitPoint origin,
            TransitPoint destination
    ) {
        List<TransitRouteResult.RouteLine> routeLines = new ArrayList<>();
        List<Object> currentCoordinate = coordinate(origin);
        List<Object> destinationCoordinate = coordinate(destination);
        int transitLineIndex = 0;

        for (Map<String, Object> subPath : subPaths) {
            if (intValue(subPath, "trafficType") == TRAFFIC_TYPE_WALK) {
                List<Object> nextCoordinate = transitLineIndex < transitRouteLines.size()
                        ? firstCoordinate(transitRouteLines.get(transitLineIndex).coordinatesJson())
                        : destinationCoordinate;
                addWalkRouteLine(routeLines, currentCoordinate, nextCoordinate);
                currentCoordinate = nextCoordinate == null ? currentCoordinate : nextCoordinate;
                continue;
            }

            if (transitLineIndex >= transitRouteLines.size()) {
                continue;
            }
            TransitRouteResult.RouteLine transitRouteLine = transitRouteLines.get(transitLineIndex);
            routeLines.add(transitRouteLine);
            List<Object> lastCoordinate = lastCoordinate(transitRouteLine.coordinatesJson());
            currentCoordinate = lastCoordinate == null ? currentCoordinate : lastCoordinate;
            transitLineIndex++;
        }

        if (routeLines.isEmpty()) {
            addWalkRouteLine(routeLines, currentCoordinate, destinationCoordinate);
        }
        return routeLines;
    }

    private void addWalkRouteLine(
            List<TransitRouteResult.RouteLine> routeLines,
            List<Object> startCoordinate,
            List<Object> endCoordinate
    ) {
        if (startCoordinate == null || endCoordinate == null || sameCoordinate(startCoordinate, endCoordinate)) {
            return;
        }
        String coordinatesJson = walkingRouteProvider.findRoute(
                        transitPoint("walk-start", startCoordinate),
                        transitPoint("walk-end", endCoordinate)
                )
                .map(WalkingRouteResult::coordinatesJson)
                .orElseGet(() -> jsonValue(List.of(startCoordinate, endCoordinate)));

        routeLines.add(new TransitRouteResult.RouteLine(
                "WALK",
                null,
                coordinatesJson
        ));
    }

    private TransitPoint transitPoint(String name, List<Object> coordinate) {
        return new TransitPoint(name, decimal(coordinate.get(0)), decimal(coordinate.get(1)));
    }

    private List<TransitRouteResult.RouteLine> detailedRouteLines(
            Map<String, Object> path,
            List<Map<String, Object>> subPaths
    ) {
        String mapObject = text(map(path, "info"), "mapObj");
        if (mapObject == null) {
            return List.of();
        }

        return odsayClient.loadLane(loadLaneMapObject(mapObject))
                .map(response -> toDetailedRouteLines(response, subPaths))
                .orElse(List.of());
    }

    private String loadLaneMapObject(String mapObject) {
        if (mapObject.contains("@")) {
            return mapObject;
        }
        return "0:0@" + mapObject;
    }

    private List<TransitRouteResult.RouteLine> toDetailedRouteLines(
            Map<String, Object> response,
            List<Map<String, Object>> subPaths
    ) {
        List<Map<String, Object>> lanes = list(map(response, "result"), "lane");
        if (lanes.isEmpty()) {
            lanes = list(response, "lane");
        }

        List<TransitRouteResult.RouteLine> detailedRouteLines = new ArrayList<>();
        if (!lanes.isEmpty()) {
            for (int index = 0; index < lanes.size(); index++) {
                Map<String, Object> lane = lanes.get(index);
                List<List<Object>> coordinates = coordinatePairs(lane);
                if (!coordinates.isEmpty()) {
                    Map<String, Object> representativeSubPath = representativeSubPath(subPaths, index);
                    String detailedLineName = firstText(lane, "name", "busNo", "busID", "laneName", "lineName");
                    detailedRouteLines.add(new TransitRouteResult.RouteLine(
                            mode(representativeSubPath),
                            detailedLineName != null ? detailedLineName : lineName(representativeSubPath),
                            jsonValue(coordinates)
                    ));
                }
            }
        }
        if (!detailedRouteLines.isEmpty()) {
            return detailedRouteLines;
        }

        List<List<Object>> coordinates = coordinatePairs(response);
        if (coordinates.isEmpty()) {
            return List.of();
        }
        Map<String, Object> representativeSubPath = representativeSubPath(subPaths, 0);
        return List.of(new TransitRouteResult.RouteLine(
                mode(representativeSubPath),
                lineName(representativeSubPath),
                jsonValue(coordinates)
        ));
    }

    private Map<String, Object> representativeSubPath(List<Map<String, Object>> subPaths, int transitIndex) {
        List<Map<String, Object>> transitSubPaths = subPaths.stream()
                .filter(subPath -> intValue(subPath, "trafficType") != TRAFFIC_TYPE_WALK)
                .toList();
        if (!transitSubPaths.isEmpty()) {
            return transitSubPaths.get(Math.min(transitIndex, transitSubPaths.size() - 1));
        }
        return subPaths.isEmpty() ? Map.of() : subPaths.get(0);
    }

    private List<List<Object>> coordinatePairs(Object value) {
        List<List<Object>> coordinates = new ArrayList<>();
        collectCoordinatePairs(value, coordinates);
        return coordinates;
    }

    @SuppressWarnings("unchecked")
    private void collectCoordinatePairs(Object value, List<List<Object>> coordinates) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> node = (Map<String, Object>) map;
            Object longitude = firstObject(node, "x", "lon", "longitude");
            Object latitude = firstObject(node, "y", "lat", "latitude");
            if (longitude != null && latitude != null) {
                coordinates.add(List.of(longitude, latitude));
            }
            node.values().forEach(child -> collectCoordinatePairs(child, coordinates));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(child -> collectCoordinatePairs(child, coordinates));
        }
    }

    private List<Object> coordinate(TransitPoint point) {
        if (point == null) {
            return null;
        }
        return List.of(point.longitude(), point.latitude());
    }

    private List<Object> firstCoordinate(String coordinatesJson) {
        List<List<Object>> coordinates = coordinates(coordinatesJson);
        return coordinates.isEmpty() ? null : coordinates.get(0);
    }

    private List<Object> lastCoordinate(String coordinatesJson) {
        List<List<Object>> coordinates = coordinates(coordinatesJson);
        return coordinates.isEmpty() ? null : coordinates.get(coordinates.size() - 1);
    }

    private List<List<Object>> coordinates(String coordinatesJson) {
        if (coordinatesJson == null || coordinatesJson.isBlank()) {
            return List.of();
        }

        List<List<Object>> coordinates = new ArrayList<>();
        Matcher matcher = COORDINATE_PAIR_PATTERN.matcher(coordinatesJson);
        while (matcher.find()) {
            coordinates.add(List.of(new BigDecimal(matcher.group(1)), new BigDecimal(matcher.group(2))));
        }
        return coordinates;
    }

    private boolean sameCoordinate(List<Object> first, List<Object> second) {
        if (first.size() < 2 || second.size() < 2) {
            return false;
        }
        return decimal(first.get(0)).compareTo(decimal(second.get(0))) == 0
                && decimal(first.get(1)).compareTo(decimal(second.get(1))) == 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private String mode(Map<String, Object> subPath) {
        int trafficType = intValue(subPath, "trafficType");
        return switch (trafficType) {
            case TRAFFIC_TYPE_SUBWAY -> "SUBWAY";
            case TRAFFIC_TYPE_BUS -> "BUS";
            case TRAFFIC_TYPE_WALK -> "WALK";
            default -> "UNKNOWN";
        };
    }

    private String lineName(Map<String, Object> subPath) {
        List<Map<String, Object>> lanes = list(subPath, "lane");
        if (lanes.isEmpty()) {
            return null;
        }
        Map<String, Object> lane = lanes.get(0);
        String subwayName = text(lane, "name");
        if (subwayName != null) {
            return subwayName;
        }
        String busNumber = text(lane, "busNo");
        if (busNumber != null) {
            return busNumber;
        }
        return text(lane, "busID");
    }

    private String coordinatesJson(Map<String, Object> subPath) {
        List<Map<String, Object>> stations = list(map(subPath, "passStopList"), "stations");
        if (stations.isEmpty()) {
            stations = list(map(subPath, "passStopList"), "stationList");
        }
        if (stations.isEmpty()) {
            return null;
        }

        List<List<Object>> coordinates = new ArrayList<>();
        for (Map<String, Object> station : stations) {
            Object longitude = firstObject(station, "x", "lon", "longitude");
            Object latitude = firstObject(station, "y", "lat", "latitude");
            if (longitude != null && latitude != null) {
                coordinates.add(List.of(longitude, latitude));
            }
        }
        if (coordinates.isEmpty()) {
            return null;
        }
        return jsonValue(coordinates);
    }

    private String rawJson(Map<String, Object> value) {
        return jsonValue(value);
    }

    private String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + escapeJson(text) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, entryValue) ->
                    entries.add("\"" + escapeJson(key.toString()) + "\":" + jsonValue(entryValue)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream()
                    .map(this::jsonValue)
                    .toList()) + "]";
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Map<?, ?> value)) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Map<String, Object> source, String key) {
        if (source == null) {
            return List.of();
        }
        Object value = source.get(key);
        if (value instanceof List<?> values) {
            return values.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        if (value instanceof Map<?, ?> singleValue) {
            return List.of((Map<String, Object>) singleValue);
        }
        return List.of();
    }

    private int intValue(Map<String, Object> source, String key) {
        Integer value = integerValue(source, key);
        return value == null ? 0 : value;
    }

    private Integer integerValue(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private String firstText(Map<String, Object> source, String... keys) {
        Object value = firstObject(source, keys);
        return value == null ? null : value.toString();
    }

    private Object firstObject(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String text(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
