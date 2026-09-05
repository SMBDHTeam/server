package com.server.spontaneous;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.location.dto.LocationSearchResponse;
import com.server.location.service.LocationSearchService;
import com.server.spontaneous.dto.Coordinate;
import org.springframework.stereotype.Service;

@Service
public class SpontaneousStartLocationSearchService {

    private final LocationSearchService locationSearchService;
    private final SpontaneousStartLocationValidator startLocationValidator;

    public SpontaneousStartLocationSearchService(
            LocationSearchService locationSearchService,
            SpontaneousStartLocationValidator startLocationValidator
    ) {
        this.locationSearchService = locationSearchService;
        this.startLocationValidator = startLocationValidator;
    }

    public LocationSearchResponse search(String keyword, int size) {
        try {
            LocationSearchResponse response = locationSearchService.search(keyword, size);
            return new LocationSearchResponse(response.items().stream()
                    .filter(item -> isBusanCandidate(item.address()))
                    .filter(item -> startLocationValidator.isBusan(new Coordinate(
                            item.latitude().doubleValue(), item.longitude().doubleValue())))
                    .toList());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE) {
                throw new BusinessException(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE, exception);
            }
            throw exception;
        }
    }

    private boolean isBusanCandidate(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        // 주소는 호출량을 줄이는 사전 필터다. 포함 여부는 반드시 행정구역 API로 확인한다.
        String region = address.strip().split("\\s+", 2)[0];
        return "부산".equals(region) || "부산광역시".equals(region);
    }
}
