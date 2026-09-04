package com.server.spontaneous;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalRegionCodeResponse;
import com.server.spontaneous.dto.Coordinate;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class SpontaneousStartLocationValidator {

    private static final String BUSAN_REGION_1_DEPTH_NAME = "부산광역시";

    private final KakaoLocalClient kakaoLocalClient;

    public SpontaneousStartLocationValidator(KakaoLocalClient kakaoLocalClient) {
        this.kakaoLocalClient = kakaoLocalClient;
    }

    public void validateBusan(Coordinate startLocation) {
        if (startLocation == null || startLocation.latitude() == null || startLocation.longitude() == null) {
            throw new BusinessException(ErrorCode.INVALID_SPONTANEOUS_TRIP_REQUEST);
        }

        KakaoLocalRegionCodeResponse response = regionCodeResponse(startLocation);
        boolean isBusan = response.documentsOrEmpty()
                .stream()
                .anyMatch(document -> BUSAN_REGION_1_DEPTH_NAME.equals(document.region1DepthName()));

        if (!isBusan) {
            throw new BusinessException(ErrorCode.SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN);
        }
    }

    private KakaoLocalRegionCodeResponse regionCodeResponse(Coordinate startLocation) {
        try {
            return kakaoLocalClient.searchRegionCode(
                    BigDecimal.valueOf(startLocation.longitude()),
                    BigDecimal.valueOf(startLocation.latitude())
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE) {
                throw new BusinessException(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE, exception);
            }
            throw exception;
        }
    }
}
