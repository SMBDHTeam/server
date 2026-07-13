package com.server.external.busanbims;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BusanBimsClient {

    private static final Logger log = LoggerFactory.getLogger(BusanBimsClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final BusanBimsProperties properties;

    public BusanBimsClient(RestClient busanBimsRestClient, BusanBimsProperties properties) {
        this.restClient = busanBimsRestClient;
        this.properties = properties;
    }

    public Optional<BusanBimsArrivalEstimate> findArrival(String busStopId, String lineName) {
        if (properties.serviceKey().isBlank() || busStopId == null || busStopId.isBlank()) {
            return Optional.empty();
        }

        try {
            String uri = UriComponentsBuilder.fromPath("/busStopArrByBstopid")
                    .queryParam("serviceKey", URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8))
                    .queryParam("bstopid", busStopId)
                    .queryParam("resultType", "json")
                    .build(true)
                    .toUriString();
            Map<String, Object> response = restClient.get()
                    .uri(URI.create(baseUrl() + uri))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (response == null || response.isEmpty()) {
                return Optional.empty();
            }
            return arrivalEstimate(response, lineName);
        } catch (RestClientResponseException exception) {
            log.warn("Busan BIMS arrival request failed. statusCode={}", exception.getStatusCode());
            return Optional.empty();
        } catch (ResourceAccessException | IllegalArgumentException exception) {
            log.warn("Busan BIMS arrival request failed. exceptionType={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<BusanBimsArrivalEstimate> arrivalEstimate(Map<String, Object> response, String lineName) {
        return arrivalItems(response).stream()
                .filter(item -> lineName == null || lineName.isBlank() || sameRoute(lineName, routeName(item)))
                .map(this::arrivalEstimate)
                .flatMap(Optional::stream)
                .min((first, second) -> Integer.compare(first.waitMinutes(), second.waitMinutes()));
    }

    private Optional<BusanBimsArrivalEstimate> arrivalEstimate(Map<String, Object> item) {
        Integer waitMinutes = firstMinutes(item,
                "min1",
                "min2",
                "remainMin",
                "restMin",
                "arrivalMin",
                "arrTime",
                "arrtime",
                "arrivalTime",
                "predictTime1",
                "predictTime2"
        );
        if (waitMinutes == null || waitMinutes < 0) {
            return Optional.empty();
        }
        return Optional.of(new BusanBimsArrivalEstimate(
                waitMinutes,
                routeName(item),
                waitMinutes >= 15 ? "LONG_WAIT" : "NORMAL"
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> arrivalItems(Object value) {
        List<Map<String, Object>> items = new ArrayList<>();
        collectArrivalItems(value, items);
        return items;
    }

    @SuppressWarnings("unchecked")
    private void collectArrivalItems(Object value, List<Map<String, Object>> items) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> node = (Map<String, Object>) map;
            if (firstObject(node, "min1", "arrTime", "arrtime", "arrivalTime", "predictTime1") != null
                    && routeName(node) != null) {
                items.add(node);
            }
            node.values().forEach(child -> collectArrivalItems(child, items));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(child -> collectArrivalItems(child, items));
        }
    }

    private Integer firstMinutes(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            Integer minutes = minutes(value);
            if (minutes != null) {
                return minutes;
            }
        }
        return null;
    }

    private Integer minutes(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().replaceAll("[^0-9]", "");
        if (text.isBlank()) {
            return null;
        }
        int number = Integer.parseInt(text);
        return number > 180 ? (int) Math.ceil(number / 60.0) : number;
    }

    private String routeName(Map<String, Object> item) {
        Object value = firstObject(item, "lineNo", "lineno", "buslinenum", "routeNo", "routeName", "busNo");
        return value == null ? null : value.toString();
    }

    private boolean sameRoute(String expected, String actual) {
        return normalizeRoute(expected).equals(normalizeRoute(actual));
    }

    private String normalizeRoute(String value) {
        return value == null ? "" : value.replaceAll("[^0-9A-Za-z가-힣]", "");
    }

    private Object firstObject(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String baseUrl() {
        String baseUrl = properties.baseUrl();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
