package com.server.external.tmap;

import com.server.external.metrics.ExternalCallMetricsCollector;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TmapWalkingClient {

    private static final Logger log = LoggerFactory.getLogger(TmapWalkingClient.class);

    private final RestClient restClient;
    private final TmapProperties properties;
    private final ExternalCallMetricsCollector metricsCollector;

    public TmapWalkingClient(RestClient tmapRestClient, TmapProperties properties) {
        this(tmapRestClient, properties, new ExternalCallMetricsCollector());
    }

    @Autowired
    public TmapWalkingClient(
            RestClient tmapRestClient,
            TmapProperties properties,
            ExternalCallMetricsCollector metricsCollector
    ) {
        this.restClient = tmapRestClient;
        this.properties = properties;
        this.metricsCollector = metricsCollector;
    }

    public Optional<TmapWalkingRoute> findWalkingRoute(
            BigDecimal startLongitude,
            BigDecimal startLatitude,
            BigDecimal endLongitude,
            BigDecimal endLatitude
    ) {
        if (properties.appKey().isBlank()) {
            return Optional.empty();
        }

        try {
            metricsCollector.recordTmapWalking();
            TmapWalkingRouteResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tmap/routes/pedestrian")
                            .queryParam("version", "1")
                            .queryParam("format", "json")
                            .build())
                    .header("appKey", properties.appKey())
                    .body(new TmapWalkingRouteRequest(
                            startLongitude.toPlainString(),
                            startLatitude.toPlainString(),
                            endLongitude.toPlainString(),
                            endLatitude.toPlainString(),
                            "WGS84GEO",
                            "WGS84GEO",
                            "start",
                            "end"
                    ))
                    .retrieve()
                    .body(TmapWalkingRouteResponse.class);
            if (response == null) {
                metricsCollector.recordFailure();
                return Optional.empty();
            }

            var coordinates = response.lineStringCoordinates();
            if (coordinates.size() < 2) {
                metricsCollector.recordFailure();
                return Optional.empty();
            }
            Integer totalSeconds = response.totalSeconds();
            if (totalSeconds == null || totalSeconds <= 0) {
                metricsCollector.recordFailure();
                return Optional.empty();
            }
            return Optional.of(new TmapWalkingRoute(totalSeconds, response.distanceMeters(), coordinates));
        } catch (RestClientResponseException exception) {
            metricsCollector.recordFailure();
            log.warn("TMAP walking route request failed. statusCode={}", exception.getStatusCode());
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException exception) {
            metricsCollector.recordFailure();
            log.warn("TMAP walking route request failed. exceptionType={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private record TmapWalkingRouteRequest(
            String startX,
            String startY,
            String endX,
            String endY,
            String reqCoordType,
            String resCoordType,
            String startName,
            String endName
    ) {
    }
}
