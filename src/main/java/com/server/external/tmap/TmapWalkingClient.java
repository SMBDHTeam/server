package com.server.external.tmap;

import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TmapWalkingClient {

    private static final Logger log = LoggerFactory.getLogger(TmapWalkingClient.class);

    private final RestClient restClient;
    private final TmapProperties properties;

    public TmapWalkingClient(RestClient tmapRestClient, TmapProperties properties) {
        this.restClient = tmapRestClient;
        this.properties = properties;
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
                return Optional.empty();
            }

            var coordinates = response.lineStringCoordinates();
            if (coordinates.size() < 2) {
                return Optional.empty();
            }
            Integer totalSeconds = response.totalSeconds();
            if (totalSeconds == null || totalSeconds <= 0) {
                return Optional.empty();
            }
            return Optional.of(new TmapWalkingRoute(totalSeconds, response.distanceMeters(), coordinates));
        } catch (RestClientResponseException exception) {
            log.warn("TMAP walking route request failed. statusCode={}", exception.getStatusCode());
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException exception) {
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
