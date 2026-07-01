package com.server.place.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.domain.PlaceImage;
import com.server.place.domain.PlaceOperatingInfo;
import com.server.place.dto.PlaceDetailResponse;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {

    private static final int DEFAULT_RADIUS_METERS = 1000;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Transactional(readOnly = true)
    public PlaceSearchResponse search(
            String keyword,
            BigDecimal longitude,
            BigDecimal latitude,
            Integer radius
    ) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasLongitude = longitude != null;
        boolean hasLatitude = latitude != null;

        if (hasKeyword && (hasLongitude || hasLatitude)) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
        }
        if (hasKeyword) {
            return searchByKeyword(keyword.trim());
        }
        if (hasLongitude || hasLatitude) {
            if (!hasLongitude || !hasLatitude) {
                throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
            }
            return searchByLocation(longitude, latitude, radius == null ? DEFAULT_RADIUS_METERS : radius);
        }
        throw new BusinessException(ErrorCode.INVALID_SCHEDULE_CONDITION);
    }

    @Transactional(readOnly = true)
    public PlaceDetailResponse getDetail(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        return toDetailResponse(place);
    }

    private PlaceSearchResponse searchByKeyword(String keyword) {
        return new PlaceSearchResponse(placeRepository.findByNameContainingIgnoreCaseOrderByNameAsc(keyword)
                .stream()
                .map(place -> toSearchItem(place, null))
                .toList());
    }

    private PlaceSearchResponse searchByLocation(BigDecimal longitude, BigDecimal latitude, int radius) {
        List<PlaceSearchResponse.Item> items = placeRepository.findAll()
                .stream()
                .map(place -> toSearchItem(place, distanceMeters(longitude, latitude, place)))
                .filter(item -> item.distanceMeters() <= radius)
                .sorted(Comparator.comparing(PlaceSearchResponse.Item::distanceMeters))
                .toList();

        return new PlaceSearchResponse(items);
    }

    private PlaceSearchResponse.Item toSearchItem(Place place, Integer distanceMeters) {
        return new PlaceSearchResponse.Item(
                place.getId(),
                place.getExternalContentId(),
                place.getName(),
                place.getCategory(),
                place.getAddress(),
                place.getLongitude(),
                place.getLatitude(),
                distanceMeters,
                place.getPrimaryImageUrl()
        );
    }

    private PlaceDetailResponse toDetailResponse(Place place) {
        return new PlaceDetailResponse(
                place.getId(),
                place.getExternalContentId(),
                place.getContentTypeId(),
                place.getName(),
                place.getAddress(),
                place.getLongitude(),
                place.getLatitude(),
                place.getDetail() == null ? null : place.getDetail().getOverview(),
                toOperatingInfo(place.getOperatingInfo()),
                place.getImages()
                        .stream()
                        .map(this::toImage)
                        .toList()
        );
    }

    private PlaceDetailResponse.OperatingInfo toOperatingInfo(PlaceOperatingInfo operatingInfo) {
        if (operatingInfo == null) {
            return null;
        }
        return new PlaceDetailResponse.OperatingInfo(
                operatingInfo.getOpeningHoursText(),
                operatingInfo.getClosedDaysText(),
                operatingInfo.getUseFeeText(),
                operatingInfo.getParkingText(),
                operatingInfo.isRequiresManualCheck()
        );
    }

    private PlaceDetailResponse.Image toImage(PlaceImage image) {
        return new PlaceDetailResponse.Image(
                image.getUrl(),
                image.getThumbnailUrl(),
                image.getCopyrightType()
        );
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
