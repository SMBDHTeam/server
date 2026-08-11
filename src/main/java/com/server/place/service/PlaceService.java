package com.server.place.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.FieldViolation;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.place.domain.Place;
import com.server.place.domain.PlaceImage;
import com.server.place.domain.PlaceOperatingInfo;
import com.server.place.dto.PlaceDetailResponse;
import com.server.place.dto.PlaceResolveRequest;
import com.server.place.dto.PlaceResolveResponse;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.support.NaverCategoryMapper;
import com.server.place.support.PlaceSource;
import com.server.place.support.PlaceCategoryLabelResolver;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {

    private static final int DEFAULT_RADIUS_METERS = 1000;
    private static final int DEFAULT_SEARCH_SIZE = 20;
    private static final int MAX_SEARCH_SIZE = 50;
    /** Kakao Local rejects a keyword search with size above this. */
    private static final int KAKAO_MAX_SEARCH_SIZE = 15;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    /** How close two rows must be before we treat them as the same real world place. */
    private static final int SAME_PLACE_RADIUS_METERS = 100;

    private final PlaceRepository placeRepository;
    private final KakaoLocalClient kakaoLocalClient;

    public PlaceService(PlaceRepository placeRepository) {
        this(placeRepository, null);
    }

    @Autowired
    public PlaceService(PlaceRepository placeRepository, KakaoLocalClient kakaoLocalClient) {
        this.placeRepository = placeRepository;
        this.kakaoLocalClient = kakaoLocalClient;
    }

    @Transactional(readOnly = true)
    public PlaceSearchResponse search(
            String keyword,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer radius
    ) {
        return search(keyword, longitude, latitude, radius, "INTERNAL", DEFAULT_SEARCH_SIZE);
    }

    @Transactional(readOnly = true)
    public PlaceSearchResponse search(
            String keyword,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer radius,
            String scope,
            Integer size
    ) {
        int resolvedSize = size == null ? DEFAULT_SEARCH_SIZE : size;
        if (resolvedSize < 1 || resolvedSize > MAX_SEARCH_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_SEARCH_REQUEST, List.of(
                    new FieldViolation("size",
                            "1 이상 %d 이하여야 합니다. 요청 값: %d".formatted(MAX_SEARCH_SIZE, resolvedSize))));
        }
        if (!("INTERNAL".equals(scope) || "ALL".equals(scope))) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_SEARCH_REQUEST, List.of(
                    new FieldViolation("scope",
                            "INTERNAL 또는 ALL 이어야 합니다. 요청 값: %s".formatted(scope))));
        }
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasLongitude = longitude != null;
        boolean hasLatitude = latitude != null;

        if (hasKeyword && (hasLongitude || hasLatitude)) {
            throw new BusinessException(ErrorCode.INVALID_PLACE_SEARCH_REQUEST, List.of(
                    new FieldViolation("keyword",
                            "keyword 검색과 좌표 검색은 함께 사용할 수 없습니다.")));
        }
        if (hasKeyword) {
            return searchByKeyword(keyword.trim(), scope, resolvedSize);
        }
        if (hasLongitude || hasLatitude) {
            if (!hasLongitude || !hasLatitude) {
                throw new BusinessException(ErrorCode.INVALID_PLACE_SEARCH_REQUEST, List.of(
                        new FieldViolation(hasLongitude ? "latitude" : "longitude",
                                "좌표 검색에는 longitude와 latitude가 모두 필요합니다.")));
            }
            return searchByLocation(longitude, latitude, radius == null ? DEFAULT_RADIUS_METERS : radius, resolvedSize);
        }
        throw new BusinessException(ErrorCode.INVALID_PLACE_SEARCH_REQUEST, List.of(
                new FieldViolation("keyword",
                        "keyword 또는 longitude·latitude 중 하나는 있어야 합니다.")));
    }

    @Transactional
    public PlaceResolveResponse resolve(PlaceResolveRequest request) {
        if (!PlaceSource.userRegistered().contains(request.source())) {
            throw new BusinessException(ErrorCode.INVALID_EXTERNAL_PLACE);
        }
        validateCoordinates(request.longitude(), request.latitude());
        Place alreadyRegistered = placeRepository
                .findBySourceAndExternalContentId(request.source(), request.externalId())
                .orElse(null);
        if (alreadyRegistered == null) {
            // The same real world place can already exist from our own ingestion, and that row
            // carries operating hours, images and a content type the external result lacks.
            // Reuse it as is rather than creating a second row or overwriting curated data.
            Place existing = findSamePlace(request).orElse(null);
            if (existing != null) {
                return toResolveResponse(existing);
            }
        }
        Place place = alreadyRegistered != null ? alreadyRegistered : new Place(
                request.source(), request.externalId(),
                NaverCategoryMapper.contentTypeId(request.category()),
                request.name(), request.category(),
                request.address(), request.longitude(), request.latitude(), null);
        place.updateResolvedPlace(
                request.name(), request.category(), request.address(), request.longitude(), request.latitude(),
                request.placeUrl());
        return toResolveResponse(placeRepository.save(place));
    }

    /**
     * Matches on proximity plus a normalized name so "해운대 해수욕장" from an external provider
     * links to our ingested "해운대해수욕장" instead of duplicating it.
     */
    private Optional<Place> findSamePlace(PlaceResolveRequest request) {
        String incomingName = normalizedName(request.name());
        if (incomingName.isEmpty()) {
            return Optional.empty();
        }
        return placeRepository.findAll().stream()
                .filter(place -> place.getLongitude() != null && place.getLatitude() != null)
                .filter(place -> distanceMeters(request.longitude(), request.latitude(), place)
                        <= SAME_PLACE_RADIUS_METERS)
                .filter(place -> {
                    String existingName = normalizedName(place.getName());
                    return !existingName.isEmpty()
                            && (existingName.equals(incomingName)
                            || existingName.contains(incomingName)
                            || incomingName.contains(existingName));
                })
                .min(Comparator.comparingInt(
                        place -> distanceMeters(request.longitude(), request.latitude(), place)));
    }

    private String normalizedName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\s·・()\\[\\]{},-]", "").toLowerCase();
    }

    private PlaceResolveResponse toResolveResponse(Place saved) {
        return new PlaceResolveResponse(
                saved.getId(), saved.getSource(), saved.getExternalContentId(), saved.getName(),
                saved.getCategory(), PlaceCategoryLabelResolver.resolve(
                        saved.getCategory(), saved.getContentTypeId()), saved.getAddress(),
                saved.getLongitude(), saved.getLatitude(), saved.getPrimaryImageUrl(), saved.getPlaceUrl(),
                true, saved.getOperatingInfo() != null);
    }

    @Transactional(readOnly = true)
    public PlaceDetailResponse getDetail(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        return toDetailResponse(place);
    }

    private PlaceSearchResponse searchByKeyword(String keyword, String scope, int size) {
        List<PlaceSearchResponse.Item> items = new ArrayList<>(placeRepository
                .findByNameContainingIgnoreCaseOrderByNameAsc(keyword).stream()
                .limit(size)
                .map(place -> toSearchItem(place, null))
                .toList());
        if (!"ALL".equals(scope) || items.size() >= size || kakaoLocalClient == null) {
            return new PlaceSearchResponse(List.copyOf(items));
        }
        Set<String> resolvedKakaoIds = new HashSet<>();
        placeRepository.findAll().stream()
                .filter(place -> "KAKAO_LOCAL".equals(place.getSource()))
                .forEach(place -> resolvedKakaoIds.add(place.getExternalContentId()));
        // Kakao rejects size above 15, and our own API allows up to 50. Passing the caller's
        // size straight through made every large request fail the whole merged search.
        int kakaoSize = Math.min(size, KAKAO_MAX_SEARCH_SIZE);
        KakaoLocalSearchResponse response = kakaoLocalClient.searchKeyword(keyword, kakaoSize);
        for (KakaoLocalSearchResponse.Document document : response.documentsOrEmpty()) {
            if (items.size() >= size) break;
            if (resolvedKakaoIds.contains(document.id())) continue;
            items.add(toExternalSearchItem(document));
        }
        return new PlaceSearchResponse(List.copyOf(items));
    }

    private PlaceSearchResponse searchByLocation(
            BigDecimal longitude,
            BigDecimal latitude,
            int radius,
            int size
    ) {
        List<PlaceSearchResponse.Item> items = placeRepository.findAll().stream()
                .map(place -> toSearchItem(place, distanceMeters(longitude, latitude, place)))
                .filter(item -> item.distanceMeters() <= radius)
                .sorted(Comparator.comparing(PlaceSearchResponse.Item::distanceMeters))
                .limit(size)
                .toList();
        return new PlaceSearchResponse(items);
    }

    private PlaceSearchResponse.Item toSearchItem(Place place, Integer distanceMeters) {
        return new PlaceSearchResponse.Item(
                place.getId(), place.getSource(), place.getExternalContentId(), place.getName(),
                place.getCategory(), PlaceCategoryLabelResolver.resolve(
                        place.getCategory(), place.getContentTypeId()),
                place.getAddress(), place.getLongitude(), place.getLatitude(),
                distanceMeters, place.getPrimaryImageUrl(), place.getPlaceUrl(), true);
    }

    private PlaceSearchResponse.Item toExternalSearchItem(KakaoLocalSearchResponse.Document document) {
        try {
            return new PlaceSearchResponse.Item(
                    null, "KAKAO_LOCAL", document.id(), document.placeName(), document.categoryName(),
                    document.addressName(), new BigDecimal(document.x()), new BigDecimal(document.y()),
                    document.distance() == null || document.distance().isBlank()
                            ? null : Integer.valueOf(document.distance()),
                    null, document.placeUrl(), false);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    private PlaceDetailResponse toDetailResponse(Place place) {
        return new PlaceDetailResponse(
                place.getId(), place.getExternalContentId(), place.getContentTypeId(), place.getName(),
                place.getAddress(), place.getLongitude(), place.getLatitude(),
                place.getDetail() == null ? null : place.getDetail().getOverview(),
                toOperatingInfo(place.getOperatingInfo()),
                place.getImages().stream().map(this::toImage).toList());
    }

    private PlaceDetailResponse.OperatingInfo toOperatingInfo(PlaceOperatingInfo operatingInfo) {
        if (operatingInfo == null) return null;
        return new PlaceDetailResponse.OperatingInfo(
                operatingInfo.getOpeningHoursText(), operatingInfo.getClosedDaysText(),
                operatingInfo.getUseFeeText(), operatingInfo.getParkingText(),
                operatingInfo.isRequiresManualCheck());
    }

    private PlaceDetailResponse.Image toImage(PlaceImage image) {
        return new PlaceDetailResponse.Image(image.getUrl(), image.getThumbnailUrl(), image.getCopyrightType());
    }

    private void validateCoordinates(BigDecimal longitude, BigDecimal latitude) {
        if (longitude.compareTo(new BigDecimal("-180")) < 0
                || longitude.compareTo(new BigDecimal("180")) > 0
                || latitude.compareTo(new BigDecimal("-90")) < 0
                || latitude.compareTo(new BigDecimal("90")) > 0) {
            throw new BusinessException(ErrorCode.INVALID_EXTERNAL_PLACE);
        }
    }

    private Integer distanceMeters(BigDecimal longitude, BigDecimal latitude, Place place) {
        double fromLongitude = Math.toRadians(longitude.doubleValue());
        double fromLatitude = Math.toRadians(latitude.doubleValue());
        double toLongitude = Math.toRadians(place.getLongitude().doubleValue());
        double toLatitude = Math.toRadians(place.getLatitude().doubleValue());
        double deltaLongitude = toLongitude - fromLongitude;
        double deltaLatitude = toLatitude - fromLatitude;
        double a = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(fromLatitude) * Math.cos(toLatitude) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }
}
