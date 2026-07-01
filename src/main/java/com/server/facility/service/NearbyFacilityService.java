package com.server.facility.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalSearchResponse;
import com.server.facility.dto.NearbyFacilityResponse;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NearbyFacilityService {

    private static final String SUPPORTED_TYPE = "CONVENIENCE_STORE";
    private static final String SOURCE = "KAKAO_LOCAL";

    private final PlaceRepository placeRepository;
    private final KakaoLocalClient kakaoLocalClient;

    public NearbyFacilityService(PlaceRepository placeRepository, KakaoLocalClient kakaoLocalClient) {
        this.placeRepository = placeRepository;
        this.kakaoLocalClient = kakaoLocalClient;
    }

    @Transactional(readOnly = true)
    public NearbyFacilityResponse search(Long placeId, String types, int radius) {
        if (!SUPPORTED_TYPE.equals(types)) {
            throw new BusinessException(ErrorCode.FACILITY_TYPE_NOT_SUPPORTED);
        }

        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        KakaoLocalSearchResponse response = kakaoLocalClient.searchConvenienceStores(
                place.getLongitude(),
                place.getLatitude(),
                radius
        );

        return new NearbyFacilityResponse(response.documentsOrEmpty()
                .stream()
                .map(this::toItem)
                .toList());
    }

    private NearbyFacilityResponse.Item toItem(KakaoLocalSearchResponse.Document document) {
        return new NearbyFacilityResponse.Item(
                document.id(),
                SUPPORTED_TYPE,
                document.placeName(),
                document.addressName(),
                new BigDecimal(document.x()),
                new BigDecimal(document.y()),
                toDistanceMeters(document.distance()),
                document.placeUrl(),
                SOURCE
        );
    }

    private Integer toDistanceMeters(String distance) {
        if (distance == null || distance.isBlank()) {
            return null;
        }
        return Integer.valueOf(distance);
    }
}
