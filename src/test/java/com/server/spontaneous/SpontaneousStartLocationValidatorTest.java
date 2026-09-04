package com.server.spontaneous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.kakao.KakaoLocalClient;
import com.server.external.kakao.KakaoLocalRegionCodeResponse;
import com.server.spontaneous.dto.Coordinate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("즉흥여행 출발지 부산 행정구역 검증")
class SpontaneousStartLocationValidatorTest {

    private final KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
    private final SpontaneousStartLocationValidator validator =
            new SpontaneousStartLocationValidator(kakaoLocalClient);

    @Test
    @DisplayName("Kakao 행정구역이 부산광역시면 허용한다")
    void allowsBusanRegion() {
        when(kakaoLocalClient.searchRegionCode(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(regionResponse("부산광역시"));

        assertThatCode(() -> validator.validateBusan(new Coordinate(35.1151, 129.0403)))
                .doesNotThrowAnyException();

        ArgumentCaptor<BigDecimal> longitude = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> latitude = ArgumentCaptor.forClass(BigDecimal.class);
        verify(kakaoLocalClient).searchRegionCode(longitude.capture(), latitude.capture());
        assertThat(longitude.getValue()).isEqualByComparingTo("129.0403");
        assertThat(latitude.getValue()).isEqualByComparingTo("35.1151");
    }

    @Test
    @DisplayName("Kakao 행정구역이 부산광역시가 아니면 400으로 차단한다")
    void rejectsOutsideBusanRegion() {
        when(kakaoLocalClient.searchRegionCode(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(regionResponse("서울특별시"));

        assertThatThrownBy(() -> validator.validateBusan(new Coordinate(37.5665, 126.9780)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SPONTANEOUS_START_LOCATION_OUTSIDE_BUSAN);
    }

    @Test
    @DisplayName("Kakao timeout은 부산 밖 판정이 아니라 provider unavailable로 변환한다")
    void mapsKakaoTimeoutToProviderUnavailable() {
        when(kakaoLocalClient.searchRegionCode(any(BigDecimal.class), any(BigDecimal.class)))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));

        assertThatThrownBy(() -> validator.validateBusan(new Coordinate(35.1151, 129.0403)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("Kakao auth/config 문제는 부산 밖 판정이 아니라 provider unavailable로 변환한다")
    void mapsKakaoAuthOrConfigFailureToProviderUnavailable() {
        when(kakaoLocalClient.searchRegionCode(any(BigDecimal.class), any(BigDecimal.class)))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE));

        assertThatThrownBy(() -> validator.validateBusan(new Coordinate(35.1151, 129.0403)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SPONTANEOUS_PROVIDER_UNAVAILABLE);
    }

    private KakaoLocalRegionCodeResponse regionResponse(String region1DepthName) {
        return new KakaoLocalRegionCodeResponse(List.of(new KakaoLocalRegionCodeResponse.Document(
                "H",
                region1DepthName + " 테스트동",
                region1DepthName,
                "테스트구",
                "테스트동",
                "",
                "0000000000",
                129.0403,
                35.1151
        )));
    }
}
