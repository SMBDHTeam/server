package com.server.schedule.planner;

import com.server.external.aitheme.PlaceThemePredictionClient;
import com.server.external.aitheme.PlaceThemePredictionClient.PlaceThemeInsight;
import com.server.place.domain.Place;
import com.server.place.support.TourApiTheme;
import com.server.place.support.TourApiThemeMapper;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlacePreferenceScorer {

    private static final int FAMILY_WALK_COMFORT_METERS = 4_000;
    private static final int RELAXED_WALK_COMFORT_METERS = 3_500;
    private static final int ACTIVE_WALK_COMFORT_METERS = 7_000;
    private static final int WEST_BUSAN_SCORE_BONUS = -2_500;
    private static final int STRONG_PREFERENCE_BONUS = -4_000;
    private static final int PREFERENCE_BONUS = -1_500;
    private static final int PROMPT_PREFERENCE_BONUS = -750;
    private static final int PREFERENCE_MISMATCH_PENALTY = 1_000;
    private static final int HILL_BURDEN_PENALTY = 6_000;
    private static final int ACTIVE_ATTRACTION_BONUS = -3_000;
    private static final int ACTIVE_MEAL_PENALTY = 1_000;
    private static final int AI_THEME_MATCH_BONUS = -1_000;
    private static final int AI_PRIMARY_THEME_BONUS = -2_000;
    private static final int AI_SECONDARY_THEME_BONUS = -1_000;
    private static final int AI_LOW_MOBILITY_BURDEN_PENALTY = 4_000;
    private static final int AI_UNFRIENDLY_PRIORITY_PENALTY = 3;
    private static final int AI_THEME_RERANK_LIMIT = 30;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private final PlaceThemePredictionClient placeThemePredictionClient;

    public PlacePreferenceScorer() {
        this(place -> Optional.empty());
    }

    @Autowired
    public PlacePreferenceScorer(PlaceThemePredictionClient placeThemePredictionClient) {
        this.placeThemePredictionClient = placeThemePredictionClient;
    }

    public ScoredPlace score(
            Place place,
            ScheduleCreateRequest.Location start,
            ScheduleCreateRequest.Location end,
            ScheduleCreateRequest request
    ) {
        Neighborhood neighborhood = Neighborhood.from(place.getLongitude(), place.getLatitude());
        Neighborhood startNeighborhood = Neighborhood.from(start.longitude(), start.latitude());
        Neighborhood endNeighborhood = Neighborhood.from(end.longitude(), end.latitude());
        int distanceFromStart = distanceMeters(
                start.longitude(), start.latitude(), place.getLongitude(), place.getLatitude());
        int distanceFromEnd = distanceMeters(
                end.longitude(), end.latitude(), place.getLongitude(), place.getLatitude());
        int nearestEndpointDistance = Math.min(distanceFromStart, distanceFromEnd);
        int neighborhoodGap = Math.min(
                neighborhood.distanceFrom(startNeighborhood),
                neighborhood.distanceFrom(endNeighborhood));
        int total = nearestEndpointDistance / 40
                + contentTypePriority(place.getContentTypeId()) * 800
                + neighborhoodGap * 700
                + neighborhood.priority() * 50
                + (neighborhood.westBusan() ? WEST_BUSAN_SCORE_BONUS : 0)
                + walkPenalty(nearestEndpointDistance, request)
                + mobilityPenalty(place, request)
                + themeScore(place, request)
                + paceScore(place, request)
                + transferPenalty(neighborhoodGap, nearestEndpointDistance, request);
        return new ScoredPlace(place, neighborhood, distanceFromStart, distanceFromEnd, total);
    }

    public int mobilityPenalty(Place place, ScheduleCreateRequest request) {
        if (!lowMobilityProfile(request)) {
            return 0;
        }
        if (isMobilityBurden(place)) {
            return HILL_BURDEN_PENALTY;
        }
        return aiInsight(place)
                .filter(insight -> !insight.lowMobilityFriendly())
                .map(ignored -> AI_LOW_MOBILITY_BURDEN_PENALTY)
                .orElse(0);
    }

    public boolean isMobilityBurden(Place place) {
        return containsAny(name(place), "감천", "흰여울", "이바구", "산복", "계단", "전망대", "중앙공원");
    }

    public boolean lowMobilityProfile(ScheduleCreateRequest request) {
        return hasAnswer(request, "COMPANION_PARENTS")
                || hasAnswer(request, "COMPANION_FAMILY_WITH_CHILD")
                || hasAnswer(request, "MOBILITY_LOW_WALK")
                || hasAnswer(request, "MOBILITY_AVOID_HILLS_STAIRS");
    }

    public int themeScore(Place place, ScheduleCreateRequest request) {
        List<String> themeAnswerIds = answerIds(request, "THEME");
        int score = themeAnswerIds.stream()
                .mapToInt(answerId -> themeScore(place, answerId))
                .min()
                .orElse(0);
        if (hasAnswer(request, "PROMPT_PREFER_SEA_VIEW") && matchesNature(place)) {
            score = Math.min(score, PROMPT_PREFERENCE_BONUS);
        }
        if (hasAnswer(request, "PROMPT_PREFER_FOOD")
                && Objects.equals(place.getContentTypeId(), "39")) {
            score = Math.min(score, PROMPT_PREFERENCE_BONUS);
        }
        return score;
    }

    public boolean hasThemePreference(ScheduleCreateRequest request) {
        return !answerIds(request, "THEME").isEmpty()
                || hasAnswer(request, "PROMPT_PREFER_SEA_VIEW")
                || hasAnswer(request, "PROMPT_PREFER_FOOD");
    }

    public List<ScoredPlace> rerankByAiTheme(
            List<ScoredPlace> candidates,
            ScheduleCreateRequest request
    ) {
        List<String> themeAnswerIds = answerIds(request, "THEME");
        if (themeAnswerIds.isEmpty() || candidates.isEmpty()) {
            return candidates;
        }
        List<ScoredPlace> adjusted = new ArrayList<>(candidates);
        int limit = Math.min(adjusted.size(), AI_THEME_RERANK_LIMIT);
        for (int index = 0; index < limit; index++) {
            ScoredPlace candidate = adjusted.get(index);
            int bonus = aiThemeBonus(candidate.place(), themeAnswerIds);
            if (bonus == 0) {
                continue;
            }
            adjusted.set(index, new ScoredPlace(
                    candidate.place(),
                    candidate.neighborhood(),
                    candidate.distanceFromStartMeters(),
                    candidate.distanceFromEndMeters(),
                    candidate.totalScore() + bonus
            ));
        }
        return adjusted.stream()
                .sorted(java.util.Comparator.comparingInt(ScoredPlace::totalScore)
                        .thenComparing(scoredPlace -> scoredPlace.place().getId() == null
                                ? Long.MAX_VALUE
                                : scoredPlace.place().getId()))
                .toList();
    }

    public Optional<PlaceThemeInsight> predictInsight(Place place) {
        return aiInsight(place);
    }

    public int aiRecommendationPriority(Place place, ScheduleCreateRequest request) {
        Optional<PlaceThemeInsight> insight = aiInsight(place);
        if (insight.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        List<String> themeAnswerIds = answerIds(request, "THEME");
        int priority = 10;
        for (String answerId : themeAnswerIds) {
            Optional<TourApiTheme> requestedTheme = TourApiTheme.fromAnswerId(answerId);
            if (requestedTheme.isEmpty()) {
                continue;
            }
            if (insight.get().primaryTheme() == requestedTheme.get()) {
                priority = Math.min(priority, 0);
            } else if (insight.get().secondaryThemes().contains(requestedTheme.get())) {
                priority = Math.min(priority, 1);
            } else if (requestedTheme.get() == TourApiTheme.FOOD && insight.get().mealPlace()) {
                priority = Math.min(priority, 1);
            } else if (requestedTheme.get() == TourApiTheme.HEALING
                    && (insight.get().semanticTags().contains("park")
                    || insight.get().semanticTags().contains("beach")
                    || insight.get().secondaryThemes().contains(TourApiTheme.HEALING))) {
                priority = Math.min(priority, 2);
            }
        }
        if (lowMobilityProfile(request) && !insight.get().lowMobilityFriendly()) {
            priority += AI_UNFRIENDLY_PRIORITY_PENALTY;
        }
        return priority;
    }

    public int paceScore(Place place, ScheduleCreateRequest request) {
        boolean active = hasAnswerContaining(request, "PACE", "ACTIVE")
                || hasAnswerContaining(request, "PACE", "PACKED")
                || hasAnswerContaining(request, "PACE", "FAST");
        if (!active) return 0;
        if (Objects.equals(place.getContentTypeId(), "12")
                || Objects.equals(place.getContentTypeId(), "15")
                || Objects.equals(place.getContentTypeId(), "28")) {
            return ACTIVE_ATTRACTION_BONUS;
        }
        return Objects.equals(place.getContentTypeId(), "39") ? ACTIVE_MEAL_PENALTY : 0;
    }

    private int themeScore(Place place, String answerId) {
        Optional<TourApiTheme> theme = TourApiTheme.fromAnswerId(answerId);
        if (theme.isPresent()) {
            return switch (theme.get()) {
                case FOOD -> themeContribution(place, answerId,
                        PlaceExperienceClassifier.AvailableExperience.FOOD,
                        PlaceExperienceClassifier.AvailableExperience.CAFE_REST);
                case NATURE -> TourApiThemeMapper.matchesTheme(place, answerId)
                        ? PREFERENCE_BONUS : PREFERENCE_MISMATCH_PENALTY;
                case CULTURE -> themeContribution(place, answerId,
                        PlaceExperienceClassifier.AvailableExperience.CULTURE_VIEW,
                        PlaceExperienceClassifier.AvailableExperience.EXHIBITION_VIEW);
                case ACTIVITY -> themeContribution(place, answerId,
                        PlaceExperienceClassifier.AvailableExperience.ACTIVITY);
                case SHOPPING -> themeContribution(place, answerId,
                        PlaceExperienceClassifier.AvailableExperience.SHOPPING,
                        PlaceExperienceClassifier.AvailableExperience.MARKET_BROWSING);
                case HEALING -> healingScore(PlaceExperienceClassifier.classify(place));
            };
        }
        PlaceExperienceClassifier.ExperienceProfile profile = PlaceExperienceClassifier.classify(place);
        return switch (answerId) {
            case "THEME_CULTURE", "THEME_HISTORY_CULTURE" -> contributionScore(
                    Math.max(profile.contribution(PlaceExperienceClassifier.AvailableExperience.CULTURE_VIEW),
                            profile.contribution(PlaceExperienceClassifier.AvailableExperience.EXHIBITION_VIEW)),
                    STRONG_PREFERENCE_BONUS, PREFERENCE_MISMATCH_PENALTY);
            case "THEME_SEA" -> contributionScore(
                    profile.contribution(PlaceExperienceClassifier.AvailableExperience.SEA_VIEW),
                    STRONG_PREFERENCE_BONUS, PREFERENCE_MISMATCH_PENALTY);
            case "THEME_NIGHT_VIEW" -> contributionScore(
                    Math.max(profile.contribution(PlaceExperienceClassifier.AvailableExperience.NIGHT_VIEW),
                            profile.contribution(PlaceExperienceClassifier.AvailableExperience.SCENIC_VIEW)),
                    PREFERENCE_BONUS, 0);
            case "THEME_EVENT" -> contributionScore(
                    profile.contribution(PlaceExperienceClassifier.AvailableExperience.EVENT_ATTENDANCE),
                    STRONG_PREFERENCE_BONUS, PREFERENCE_MISMATCH_PENALTY);
            case "THEME_LOCAL" -> profile.atmospheres().contains(PlaceExperienceClassifier.AtmosphereType.LOCAL)
                    ? PREFERENCE_BONUS : 0;
            default -> 0;
        };
    }

    private int themeContribution(
            Place place,
            String answerId,
            PlaceExperienceClassifier.AvailableExperience primary,
            PlaceExperienceClassifier.AvailableExperience... secondary
    ) {
        Optional<TourApiTheme> requestedTheme = TourApiTheme.fromAnswerId(answerId);
        if (!TourApiThemeMapper.matchesTheme(place, answerId)) {
            return requestedTheme.map(theme -> aiThemeContribution(place, theme))
                    .orElse(PREFERENCE_MISMATCH_PENALTY);
        }
        PlaceExperienceClassifier.ExperienceProfile profile = PlaceExperienceClassifier.classify(place);
        int best = profile.contribution(primary);
        for (PlaceExperienceClassifier.AvailableExperience candidate : secondary) {
            best = Math.max(best, profile.contribution(candidate));
        }
        return contributionScore(best > 0 ? best : 100, STRONG_PREFERENCE_BONUS, PREFERENCE_MISMATCH_PENALTY);
    }

    private int aiThemeBonus(Place place, List<String> themeAnswerIds) {
        if (themeAnswerIds.stream().anyMatch(answerId -> TourApiThemeMapper.matchesTheme(place, answerId))) {
            return 0;
        }
        Optional<TourApiTheme> predictedTheme = placeThemePredictionClient.predictPrimaryTheme(place);
        if (predictedTheme.isEmpty()) {
            return 0;
        }
        return themeAnswerIds.stream()
                .map(TourApiTheme::fromAnswerId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(requested -> themeMatches(predictedTheme.get(), requested))
                ? AI_THEME_MATCH_BONUS : 0;
    }

    private boolean themeMatches(TourApiTheme predictedTheme, TourApiTheme requestedTheme) {
        if (requestedTheme == TourApiTheme.HEALING) {
            return predictedTheme == TourApiTheme.HEALING
                    || predictedTheme == TourApiTheme.NATURE
                    || predictedTheme == TourApiTheme.FOOD;
        }
        return predictedTheme == requestedTheme;
    }

    private int healingScore(PlaceExperienceClassifier.ExperienceProfile profile) {
        return profile.contribution(PlaceExperienceClassifier.AvailableExperience.REST) > 0
                || profile.contribution(PlaceExperienceClassifier.AvailableExperience.PARK_REST) > 0
                || profile.contribution(PlaceExperienceClassifier.AvailableExperience.NATURE_WALK) > 0
                || profile.contribution(PlaceExperienceClassifier.AvailableExperience.CAFE_REST) > 0
                ? PREFERENCE_BONUS : 0;
    }

    private int aiThemeContribution(Place place, TourApiTheme requestedTheme) {
        return aiInsight(place)
                .map(insight -> {
                    if (insight.primaryTheme() == requestedTheme) {
                        return AI_PRIMARY_THEME_BONUS;
                    }
                    if (insight.secondaryThemes().contains(requestedTheme)
                            || (requestedTheme == TourApiTheme.FOOD && insight.mealPlace())
                            || (requestedTheme == TourApiTheme.HEALING
                            && (insight.secondaryThemes().contains(TourApiTheme.HEALING)
                            || insight.semanticTags().contains("park")
                            || insight.semanticTags().contains("beach")))) {
                        return AI_SECONDARY_THEME_BONUS;
                    }
                    return PREFERENCE_MISMATCH_PENALTY;
                })
                .orElse(PREFERENCE_MISMATCH_PENALTY);
    }

    private Optional<PlaceThemeInsight> aiInsight(Place place) {
        return placeThemePredictionClient.predictInsight(place);
    }

    private int contributionScore(int contribution, int fullBonus, int mismatchPenalty) {
        if (contribution <= 0) return mismatchPenalty;
        return fullBonus * Math.min(contribution, 100) / 100;
    }

    public int distanceMeters(
            BigDecimal fromLongitude,
            BigDecimal fromLatitude,
            BigDecimal toLongitude,
            BigDecimal toLatitude
    ) {
        double fromLongitudeRadians = Math.toRadians(fromLongitude.doubleValue());
        double fromLatitudeRadians = Math.toRadians(fromLatitude.doubleValue());
        double toLongitudeRadians = Math.toRadians(toLongitude.doubleValue());
        double toLatitudeRadians = Math.toRadians(toLatitude.doubleValue());
        double deltaLongitude = toLongitudeRadians - fromLongitudeRadians;
        double deltaLatitude = toLatitudeRadians - fromLatitudeRadians;
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(fromLatitudeRadians) * Math.cos(toLatitudeRadians)
                * Math.pow(Math.sin(deltaLongitude / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }

    private int walkPenalty(int nearestEndpointDistance, ScheduleCreateRequest request) {
        int comfortMeters = hasAnswer(request, "COMPANION_PARENTS")
                ? FAMILY_WALK_COMFORT_METERS : ACTIVE_WALK_COMFORT_METERS;
        if (hasAnswer(request, "COMPANION_FAMILY_WITH_CHILD") || hasAnswer(request, "MOBILITY_LOW_WALK")) {
            comfortMeters = Math.min(comfortMeters, FAMILY_WALK_COMFORT_METERS);
        }
        if (hasAnswer(request, "MOBILITY_AVOID_HILLS_STAIRS")) {
            comfortMeters = Math.min(comfortMeters, RELAXED_WALK_COMFORT_METERS);
        }
        if (hasAnswer(request, "PROMPT_LOW_WALKING")) {
            comfortMeters = Math.min(comfortMeters, 5_000);
        }
        if (hasAnswer(request, "MOBILITY_OK_HILLS")) {
            comfortMeters = ACTIVE_WALK_COMFORT_METERS;
        }
        if (hasAnswerContaining(request, "PACE", "SLOW") || hasAnswerContaining(request, "PACE", "RELAX")) {
            comfortMeters = Math.min(comfortMeters, RELAXED_WALK_COMFORT_METERS);
        }
        if (hasAnswerContaining(request, "PACE", "FAST")
                || hasAnswerContaining(request, "PACE", "ACTIVE")
                || hasAnswerContaining(request, "PACE", "PACKED")) {
            comfortMeters = ACTIVE_WALK_COMFORT_METERS;
        }
        return nearestEndpointDistance <= comfortMeters ? 0 : (nearestEndpointDistance - comfortMeters) / 10;
    }

    private int transferPenalty(int neighborhoodGap, int nearestEndpointDistance, ScheduleCreateRequest request) {
        int penalty = neighborhoodGap * 250;
        if (nearestEndpointDistance > 12_000) {
            penalty += (nearestEndpointDistance - 12_000) / 30;
        }
        if (hasAnswer(request, "TRANSIT_SIMPLE")) {
            penalty += neighborhoodGap * 300;
        }
        if (hasAnswer(request, "TRANSIT_FAST") && nearestEndpointDistance > 15_000) {
            penalty += (nearestEndpointDistance - 15_000) / 25;
        }
        return penalty;
    }

    private int contentTypePriority(String contentTypeId) {
        if (Objects.equals(contentTypeId, "12")) return 0;
        if (Objects.equals(contentTypeId, "15") || Objects.equals(contentTypeId, "28")) return 1;
        if (Objects.equals(contentTypeId, "14") || Objects.equals(contentTypeId, "38")) return 2;
        if (Objects.equals(contentTypeId, "39")) return 3;
        return 4;
    }

    private boolean hasAnswer(ScheduleCreateRequest request, String answerId) {
        return request.selectedAnswers().stream().anyMatch(answer -> answerId.equals(answer.answerId()));
    }

    private boolean hasAnswerContaining(ScheduleCreateRequest request, String questionId, String token) {
        return request.selectedAnswers().stream()
                .filter(answer -> questionId.equals(answer.questionId()))
                .map(ScheduleCreateRequest.SelectedAnswer::answerId)
                .filter(Objects::nonNull)
                .anyMatch(answerId -> answerId.contains(token));
    }

    private List<String> answerIds(ScheduleCreateRequest request, String questionId) {
        return request.selectedAnswers().stream()
                .filter(answer -> questionId.equals(answer.questionId()))
                .map(ScheduleCreateRequest.SelectedAnswer::answerId)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean matchesNature(Place place) {
        PlaceExperienceClassifier.ExperienceProfile profile = PlaceExperienceClassifier.classify(place);
        return profile.environments().contains(PlaceExperienceClassifier.EnvironmentType.COASTAL)
                || profile.environments().contains(PlaceExperienceClassifier.EnvironmentType.GREEN);
    }

    private String name(Place place) {
        return Optional.ofNullable(place.getName()).orElse("");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    public record ScoredPlace(
            Place place,
            Neighborhood neighborhood,
            int distanceFromStartMeters,
            int distanceFromEndMeters,
            int totalScore
    ) {
    }

    public enum Neighborhood {
        BUSAN_STATION("129.0403", "35.1151", 0, false), NAMPO("129.0327", "35.1000", 1, false),
        SEOMYEON("129.0590", "35.1577", 2, false), HAEUNDAE("129.1604", "35.1587", 3, false),
        GWANGALLI("129.1187", "35.1532", 4, false), GAMCHEON("129.0107", "35.0975", 5, true),
        YEONGDO("129.0799", "35.0512", 6, false), SONGDO("129.0176", "35.0763", 7, true),
        KYUNGSUNG_UNIV("129.1010", "35.1376", 8, false), GIJANG("129.2150", "35.2440", 9, false),
        GEUMJEONG("129.0910", "35.2429", 10, false), GANGSEO("128.9350", "35.1540", 11, true);

        private final BigDecimal longitude;
        private final BigDecimal latitude;
        private final int priority;
        private final boolean westBusan;

        Neighborhood(String longitude, String latitude, int priority, boolean westBusan) {
            this.longitude = new BigDecimal(longitude);
            this.latitude = new BigDecimal(latitude);
            this.priority = priority;
            this.westBusan = westBusan;
        }

        public int priority() { return priority; }
        public boolean westBusan() { return westBusan; }
        public int distanceFrom(Neighborhood other) {
            return distanceMeters(longitude, latitude, other.longitude, other.latitude) / 3_000;
        }
        public static Neighborhood from(BigDecimal longitude, BigDecimal latitude) {
            Neighborhood nearest = BUSAN_STATION;
            int distance = Integer.MAX_VALUE;
            for (Neighborhood candidate : values()) {
                int candidateDistance = distanceMeters(longitude, latitude, candidate.longitude, candidate.latitude);
                if (candidateDistance < distance) {
                    nearest = candidate;
                    distance = candidateDistance;
                }
            }
            return nearest;
        }
        private static int distanceMeters(BigDecimal fromLon, BigDecimal fromLat, BigDecimal toLon, BigDecimal toLat) {
            double fromLonRad = Math.toRadians(fromLon.doubleValue());
            double fromLatRad = Math.toRadians(fromLat.doubleValue());
            double toLonRad = Math.toRadians(toLon.doubleValue());
            double toLatRad = Math.toRadians(toLat.doubleValue());
            double deltaLon = toLonRad - fromLonRad;
            double deltaLat = toLatRad - fromLatRad;
            double a = Math.pow(Math.sin(deltaLat / 2), 2)
                    + Math.cos(fromLatRad) * Math.cos(toLatRad) * Math.pow(Math.sin(deltaLon / 2), 2);
            return (int) Math.round(EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
        }
    }
}
