package com.server.transit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.external.busanbims.BusanBimsArrivalEstimate;
import com.server.external.busanbims.BusanBimsClient;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("부산 BIMS 실시간 대중교통 Provider")
class BusanBimsTransitRealtimeProviderTest {

    private final BusanBimsClient busanBimsClient = Mockito.mock(BusanBimsClient.class);
    private final BusanBimsTransitRealtimeProvider provider = new BusanBimsTransitRealtimeProvider(busanBimsClient);

    @Test
    @DisplayName("버스 도착 대기가 길면 예상 이동시간 보정분을 반환한다")
    void adjustmentReturnsPenaltyForLongBusWait() {
        when(busanBimsClient.findArrival("12345", "1001"))
                .thenReturn(Optional.of(new BusanBimsArrivalEstimate(19, "1001", "LONG_WAIT")));

        TransitRealtimeAdjustment adjustment = provider.adjustment(new TransitRealtimeRequest(
                "BUS",
                "1001",
                "부산역",
                "광안리",
                "12345",
                "67890"
        ));

        assertThat(adjustment.extraMinutes()).isEqualTo(12);
        assertThat(adjustment.status()).isEqualTo("LONG_WAIT");
        assertThat(adjustment.detail()).contains("waitMinutes=19");
    }

    @Test
    @DisplayName("버스가 아니거나 정류장 ID가 없으면 외부 API를 호출하지 않는다")
    void adjustmentSkipsUnsupportedRequest() {
        TransitRealtimeAdjustment subwayAdjustment = provider.adjustment(new TransitRealtimeRequest(
                "SUBWAY",
                "부산 1호선",
                "부산역",
                "남포",
                "12345",
                "67890"
        ));
        TransitRealtimeAdjustment missingStationAdjustment = provider.adjustment(new TransitRealtimeRequest(
                "BUS",
                "1001",
                "부산역",
                "광안리",
                null,
                "67890"
        ));

        assertThat(subwayAdjustment.extraMinutes()).isZero();
        assertThat(missingStationAdjustment.extraMinutes()).isZero();
        verify(busanBimsClient, never()).findArrival(Mockito.any(), Mockito.any());
    }
}
