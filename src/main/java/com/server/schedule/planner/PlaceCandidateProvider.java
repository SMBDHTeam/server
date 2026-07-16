package com.server.schedule.planner;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.domain.PlaceIngestionStatus;
import com.server.place.repository.PlaceRepository;
import com.server.schedule.domain.ScheduleDay;
import com.server.schedule.dto.ScheduleCreateRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class PlaceCandidateProvider {

    private static final int AUTO_CANDIDATE_RADIUS_METERS = 45_000;
    private static final int MIN_DISTANCE_FROM_ENDPOINT_METERS = 700;
    private static final int MIN_DISTANCE_BETWEEN_PLACES_METERS = 900;
    private static final int OPTIONAL_CANDIDATE_POOL_SURPLUS = 6;

    private final PlaceRepository placeRepository;
    private final PlacePreferenceScorer scorer;

    public PlaceCandidateProvider(PlaceRepository placeRepository, PlacePreferenceScorer scorer) {
        this.placeRepository = placeRepository;
        this.scorer = scorer;
    }

    public ResolvedPlaces resolve(
            ScheduleCreateRequest request,
            List<Integer> dailyStopTargets,
            List<ScheduleDay> days
    ) {
        int totalTargetCount = dailyStopTargets.stream().mapToInt(Integer::intValue).sum();
        List<Long> distinctMustVisitIds = new ArrayList<>(new LinkedHashSet<>(request.mustVisitPlaceIdsOrEmpty()));
        List<Place> requiredPlaces = requiredPlaces(distinctMustVisitIds);
        Set<Long> requiredIds = Set.copyOf(distinctMustVisitIds);
        int desiredMealPlaces = 0;
        for (int index = 0; index < days.size(); index++) {
            desiredMealPlaces += MealTimePolicy.requiredMealStops(days.get(index), dailyStopTargets.get(index));
        }
        int recommendationCount = Math.max(0, totalTargetCount - requiredPlaces.size());
        int candidatePoolCount = recommendationCount == 0
                ? 0 : recommendationCount + OPTIONAL_CANDIDATE_POOL_SURPLUS;
        int missingMealPlaces = Math.max(0, desiredMealPlaces
                - (int) requiredPlaces.stream().filter(MealTimePolicy::isMealPlace).count());
        List<Place> recommendations = recommendations(
                request, candidatePoolCount, requiredIds, requiredPlaces,
                Math.min(candidatePoolCount, missingMealPlaces));
        List<Place> places = new ArrayList<>(requiredPlaces);
        places.addAll(recommendations);
        if (places.isEmpty()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        int mealPlaceCount = (int) places.stream().filter(MealTimePolicy::isMealPlace).count();
        return new ResolvedPlaces(List.copyOf(places), requiredIds, mealPlaceCount);
    }

    private List<Place> requiredPlaces(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Place> placeById = placeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        List<Place> places = new ArrayList<>();
        for (Long id : ids) {
            Place place = placeById.get(id);
            if (place == null) {
                throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
            }
            places.add(place);
        }
        return places;
    }

    private List<Place> recommendations(
            ScheduleCreateRequest request,
            int targetCount,
            Set<Long> excludedIds,
            List<Place> requiredPlaces,
            int mealTargetCount
    ) {
        if (targetCount == 0) {
            return List.of();
        }
        ScheduleCreateRequest.Location start = overallStart(request);
        ScheduleCreateRequest.Location end = overallEnd(request);
        List<PlacePreferenceScorer.ScoredPlace> candidates = placeRepository.findAll().stream()
                .filter(this::usable)
                .filter(place -> !excludedIds.contains(place.getId()))
                .map(place -> scorer.score(place, start, end, request))
                .filter(candidate -> candidate.distanceFromStartMeters() <= AUTO_CANDIDATE_RADIUS_METERS
                        || candidate.distanceFromEndMeters() <= AUTO_CANDIDATE_RADIUS_METERS)
                .filter(candidate -> candidate.distanceFromStartMeters() >= MIN_DISTANCE_FROM_ENDPOINT_METERS
                        && candidate.distanceFromEndMeters() >= MIN_DISTANCE_FROM_ENDPOINT_METERS)
                .sorted(Comparator.comparingInt(PlacePreferenceScorer.ScoredPlace::totalScore)
                        .thenComparing(candidate -> candidate.place().getId()))
                .toList();
        List<Place> selected = new ArrayList<>();
        Set<String> contentTypes = new HashSet<>();
        selectMealPlaces(candidates, selected, contentTypes, mealTargetCount);
        selectStrongPreferences(candidates, selected, requiredPlaces, contentTypes, targetCount, request);
        selectExperienceDiverse(candidates, selected, requiredPlaces, contentTypes, targetCount);
        select(candidates, selected, requiredPlaces, contentTypes, targetCount, true);
        select(candidates, selected, requiredPlaces, contentTypes, targetCount, false);
        return List.copyOf(selected);
    }

    private void selectMealPlaces(
            List<PlacePreferenceScorer.ScoredPlace> candidates,
            List<Place> selected,
            Set<String> contentTypes,
            int mealTargetCount
    ) {
        for (PlacePreferenceScorer.ScoredPlace candidate : candidates) {
            if (selected.size() >= mealTargetCount) return;
            Place place = candidate.place();
            if (!MealTimePolicy.isMealPlace(place) || selected.contains(place)) {
                continue;
            }
            selected.add(place);
            contentTypes.add(place.getContentTypeId() == null ? "" : place.getContentTypeId());
        }
    }

    private void selectStrongPreferences(
            List<PlacePreferenceScorer.ScoredPlace> candidates,
            List<Place> selected,
            List<Place> requiredPlaces,
            Set<String> contentTypes,
            int targetCount,
            ScheduleCreateRequest request
    ) {
        for (PlacePreferenceScorer.ScoredPlace candidate : candidates) {
            if (selected.size() >= targetCount) return;
            Place place = candidate.place();
            if (scorer.themeScore(place, request) >= 0
                    || scorer.mobilityPenalty(place, request) > 0
                    || exceedsStrongPreferenceDiversity(place, requiredPlaces, selected)
                    || tooClose(place, requiredPlaces)
                    || tooClose(place, selected)) {
                continue;
            }
            selected.add(place);
            contentTypes.add(place.getContentTypeId() == null ? "" : place.getContentTypeId());
        }
    }

    private void selectExperienceDiverse(
            List<PlacePreferenceScorer.ScoredPlace> candidates,
            List<Place> selected,
            List<Place> requiredPlaces,
            Set<String> contentTypes,
            int targetCount
    ) {
        Set<PlaceExperienceClassifier.ExperienceType> selectedTypes = Stream
                .concat(requiredPlaces.stream(), selected.stream())
                .map(PlaceExperienceClassifier::classify)
                .map(PlaceExperienceClassifier.ExperienceProfile::type)
                .collect(Collectors.toSet());
        for (PlacePreferenceScorer.ScoredPlace candidate : candidates) {
            if (selected.size() >= targetCount) return;
            Place place = candidate.place();
            PlaceExperienceClassifier.ExperienceType type = PlaceExperienceClassifier.classify(place).type();
            if (selected.contains(place)
                    || type == PlaceExperienceClassifier.ExperienceType.OTHER
                    || selectedTypes.contains(type)
                    || tooClose(place, requiredPlaces)
                    || tooClose(place, selected)) {
                continue;
            }
            selected.add(place);
            selectedTypes.add(type);
            contentTypes.add(place.getContentTypeId() == null ? "" : place.getContentTypeId());
        }
    }

    private boolean exceedsStrongPreferenceDiversity(
            Place place,
            List<Place> requiredPlaces,
            List<Place> selected
    ) {
        PlaceExperienceClassifier.ExperienceProfile profile = PlaceExperienceClassifier.classify(place);
        long sameTypeCount = Stream.concat(requiredPlaces.stream(), selected.stream())
                .map(PlaceExperienceClassifier::classify)
                .filter(selectedProfile -> selectedProfile.type() == profile.type())
                .count();
        long sameSemanticGroupCount = Stream.concat(requiredPlaces.stream(), selected.stream())
                .map(PlaceExperienceClassifier::classify)
                .filter(selectedProfile -> selectedProfile.semanticGroup() == profile.semanticGroup())
                .count();
        return sameTypeCount >= 2 || sameSemanticGroupCount >= 3;
    }

    private void select(
            List<PlacePreferenceScorer.ScoredPlace> candidates,
            List<Place> selected,
            List<Place> requiredPlaces,
            Set<String> contentTypes,
            int targetCount,
            boolean requireNewContentType
    ) {
        for (PlacePreferenceScorer.ScoredPlace candidate : candidates) {
            if (selected.size() >= targetCount) return;
            Place place = candidate.place();
            if (selected.contains(place)) continue;
            String contentType = place.getContentTypeId() == null ? "" : place.getContentTypeId();
            if (requireNewContentType && contentTypes.contains(contentType)) continue;
            if (tooClose(place, requiredPlaces)) continue;
            if (requireNewContentType && tooClose(place, selected)) continue;
            selected.add(place);
            contentTypes.add(contentType);
        }
    }

    private boolean tooClose(Place place, List<Place> selected) {
        return selected.stream().anyMatch(other -> scorer.distanceMeters(
                place.getLongitude(), place.getLatitude(), other.getLongitude(), other.getLatitude()
        ) < MIN_DISTANCE_BETWEEN_PLACES_METERS);
    }

    private boolean usable(Place place) {
        return place.getId() != null && place.getLongitude() != null && place.getLatitude() != null
                && place.getName() != null && !place.getName().contains("테스트")
                && place.getIngestionStatus() == PlaceIngestionStatus.SYNCED;
    }

    private ScheduleCreateRequest.Location overallStart(ScheduleCreateRequest request) {
        return request.daysOrEmpty().stream().filter(day -> day.dayNo() == 1)
                .map(ScheduleCreateRequest.DayCondition::startLocation).findFirst().orElse(request.startLocation());
    }

    private ScheduleCreateRequest.Location overallEnd(ScheduleCreateRequest request) {
        int lastDay = (int) java.time.temporal.ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        return request.daysOrEmpty().stream().filter(day -> day.dayNo() == lastDay)
                .map(ScheduleCreateRequest.DayCondition::endLocation).findFirst().orElse(request.endLocation());
    }

    public record ResolvedPlaces(
            List<Place> places,
            Set<Long> mustVisitPlaceIds,
            int mealPlaceCount
    ) {
    }
}
