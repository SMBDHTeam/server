package com.server.external.aitheme;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.server.place.domain.Place;
import com.server.place.support.TourApiTheme;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiPlaceThemeClient implements PlaceThemePredictionClient {

    private static final Logger log = LoggerFactory.getLogger(AiPlaceThemeClient.class);

    private final RestClient restClient;
    private final AiPlaceThemeProperties properties;
    private final Map<Long, Optional<PlaceThemeInsight>> cache = new ConcurrentHashMap<>();

    public AiPlaceThemeClient(
            @Qualifier("aiPlaceThemeRestClient") RestClient aiPlaceThemeRestClient,
            AiPlaceThemeProperties properties
    ) {
        this.restClient = aiPlaceThemeRestClient;
        this.properties = properties;
    }

    @Override
    public Optional<TourApiTheme> predictPrimaryTheme(Place place) {
        return predictInsight(place).map(PlaceThemeInsight::primaryTheme);
    }

    @Override
    public Optional<PlaceThemeInsight> predictInsight(Place place) {
        if (!properties.enabled() || place == null) {
            return Optional.empty();
        }
        Long placeId = place.getId();
        if (placeId == null) {
            return requestInsight(place);
        }
        return cache.computeIfAbsent(placeId, ignored -> requestInsight(place));
    }

    private Optional<PlaceThemeInsight> requestInsight(Place place) {
        try {
            PredictResponse response = restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PredictRequest.from(place))
                    .retrieve()
                    .body(PredictResponse.class);
            log.debug("AI place insight response: placeId={}, name={}, decisionSource={}, themeAnswerId={}, reason={}",
                    place.getId(), place.getName(),
                    response == null ? null : response.decisionSource(),
                    response == null ? null : response.themeAnswerId(),
                    response == null ? null : response.reason());
            if (response == null
                    || response.themeAnswerId() == null
                    || response.themeAnswerId().isBlank()) {
                log.debug("AI place insight ignored: placeId={}, name={}", place.getId(), place.getName());
                return Optional.empty();
            }
            TourApiTheme primaryTheme = TourApiTheme.fromAnswerId(response.themeAnswerId())
                    .orElse(null);
            if (primaryTheme == null) {
                return Optional.empty();
            }
            List<TourApiTheme> secondaryThemes = Optional.ofNullable(response.secondaryThemes()).orElse(List.of())
                    .stream()
                    .map(AiPlaceThemeClient::toTheme)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList();
            return Optional.of(new PlaceThemeInsight(
                    primaryTheme,
                    secondaryThemes,
                    Optional.ofNullable(response.semanticTags()).orElse(List.of()),
                    Boolean.TRUE.equals(response.isMealPlace()),
                    !Boolean.FALSE.equals(response.isLowMobilityFriendly()),
                    response.clusterKey(),
                    response.reason()
            ));
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("AI place insight request failed: placeId={}, name={}", place.getId(), place.getName(), exception);
            return Optional.empty();
        }
    }

    private static Optional<TourApiTheme> toTheme(String themeName) {
        if (themeName == null || themeName.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TourApiTheme.valueOf(themeName.trim().toUpperCase()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    record PredictRequest(
            String title,
            String overview,
            String addr1,
            String contenttypeid,
            String firstimage,
            int detail_image_count,
            String parking,
            String restdate,
            String usetime,
            String chkpet,
            String cat3,
            Double mapx,
            Double mapy
    ) {
        static PredictRequest from(Place place) {
            return new PredictRequest(
                    place.getName(),
                    null,
                    place.getAddress(),
                    place.getContentTypeId(),
                    place.getPrimaryImageUrl(),
                    0,
                    null,
                    null,
                    null,
                    null,
                    place.getCategory(),
                    place.getLongitude() == null ? null : place.getLongitude().doubleValue(),
                    place.getLatitude() == null ? null : place.getLatitude().doubleValue()
            );
        }
    }

    record PredictResponse(
            @JsonProperty("primary_category") String primaryCategory,
            @JsonProperty("theme_answer_id") String themeAnswerId,
            @JsonProperty("decision_source") String decisionSource,
            @JsonProperty("secondary_themes") List<String> secondaryThemes,
            @JsonProperty("semantic_tags") List<String> semanticTags,
            @JsonProperty("is_meal_place") Boolean isMealPlace,
            @JsonProperty("is_low_mobility_friendly") Boolean isLowMobilityFriendly,
            @JsonProperty("cluster_key") String clusterKey,
            @JsonProperty("reason") String reason
    ) {
    }
}
