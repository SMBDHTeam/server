package com.server.transit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.odsay.OdsayClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("ODsay 대중교통 경로 Provider")
class OdsayTransitRouteProviderTest {

    private final OdsayClient odsayClient = Mockito.mock(OdsayClient.class);
    private final WalkingRouteProvider walkingRouteProvider = Mockito.mock(WalkingRouteProvider.class);
    private final TransitRealtimeProvider transitRealtimeProvider = Mockito.mock(TransitRealtimeProvider.class);
    private final OdsayTransitRouteProvider provider = new OdsayTransitRouteProvider(
            odsayClient,
            walkingRouteProvider,
            transitRealtimeProvider
    );

    @Test
    @DisplayName("가장 빠른 경로를 내부 대중교통 경로로 변환한다")
    void findRouteReturnsFastestTransitRoute() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint destination = point("가덕도 등대", "128.8294", "35.0241");
        when(odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        )).thenReturn(response());
        when(odsayClient.loadLane("0:0@2823889:1:66:103")).thenReturn(Optional.empty());
        when(walkingRouteProvider.findRoute(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        when(transitRealtimeProvider.adjustment(Mockito.any()))
                .thenReturn(TransitRealtimeAdjustment.none());

        TransitRouteResult result = provider.findRoute(origin, destination);

        assertThat(result.totalMinutes()).isEqualTo(42);
        assertThat(result.fareAmount()).isEqualTo(1550);
        assertThat(result.segments()).hasSize(3);
        assertThat(result.segments().get(0).mode()).isEqualTo("WALK");
        assertThat(result.segments().get(1).mode()).isEqualTo("BUS");
        assertThat(result.segments().get(1).lineName()).isEqualTo("1009");
        assertThat(result.segments().get(2).mode()).isEqualTo("WALK");
        assertThat(result.routeLines()).hasSize(2);
        assertThat(result.routeLines().get(0).coordinatesJson()).contains("129.0403", "35.1151");
        assertThat(result.routeLines().get(0).fallbackUsed()).isTrue();
        assertThat(result.routeLines().get(1).mode()).isEqualTo("WALK");
        assertThat(result.routeLines().get(1).fallbackUsed()).isTrue();
        assertThat(result.routeLines().get(1).coordinatesJson()).contains("128.8294", "35.0241");
        assertThat(result.rawJson()).contains("\"totalTime\":42");
    }

    @Test
    @DisplayName("후보 비교용 경량 경로는 상세 좌표와 실시간 Provider를 호출하지 않는다")
    void findRouteEstimateSkipsDetailedProviders() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint destination = point("가덕도 등대", "128.8294", "35.0241");
        when(odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        )).thenReturn(response());

        TransitRouteEstimate estimate = provider.findRouteEstimate(origin, destination);
        TransitRouteResult result = estimate.route();

        assertThat(estimate.detailLevel()).isEqualTo(TransitRouteEstimate.DetailLevel.ESTIMATE);
        assertThat(estimate.requiresDetail()).isTrue();
        assertThat(result.totalMinutes()).isEqualTo(42);
        assertThat(result.segments()).hasSize(3);
        assertThat(result.routeLines()).isEmpty();
        assertThat(result.warnings()).containsExactly("후보 순서 비교용 경량 경로입니다.");
        verify(odsayClient, Mockito.never()).loadLane(Mockito.anyString());
        verify(walkingRouteProvider, Mockito.never()).findRoute(Mockito.any(), Mockito.any());
        verify(transitRealtimeProvider, Mockito.never()).adjustment(Mockito.any());
    }

    @Test
    @DisplayName("경량 경로를 상세화할 때 ODsay 경로검색 결과를 재사용한다")
    void findRouteDetailReusesEstimatedPath() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint destination = point("가덕도 등대", "128.8294", "35.0241");
        when(odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        )).thenReturn(response());
        when(odsayClient.loadLane("0:0@2823889:1:66:103")).thenReturn(Optional.empty());
        when(walkingRouteProvider.findRoute(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        when(transitRealtimeProvider.adjustment(Mockito.any()))
                .thenReturn(TransitRealtimeAdjustment.none());

        TransitRouteEstimate estimate = provider.findRouteEstimate(origin, destination);
        TransitRouteResult detailed = provider.findRouteDetail(origin, destination, estimate);

        assertThat(detailed.totalMinutes()).isEqualTo(42);
        assertThat(detailed.routeLines()).hasSize(2);
        verify(odsayClient, Mockito.times(1)).searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        );
        verify(odsayClient).loadLane("0:0@2823889:1:66:103");
    }

    @Test
    @DisplayName("상세화 endpoint가 탐색 endpoint와 다르면 경로를 다시 조회한다")
    void findRouteDetailSearchesAgainForDifferentEndpoint() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint estimatedDestination = point("가덕도 등대", "128.8294", "35.0241");
        TransitPoint requestedDestination = point("광안리", "129.1187", "35.1532");
        when(odsayClient.searchPublicTransitPath(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
        )).thenReturn(response());
        when(odsayClient.loadLane("0:0@2823889:1:66:103")).thenReturn(Optional.empty());
        when(walkingRouteProvider.findRoute(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        when(transitRealtimeProvider.adjustment(Mockito.any()))
                .thenReturn(TransitRealtimeAdjustment.none());

        TransitRouteEstimate estimate = provider.findRouteEstimate(origin, estimatedDestination);
        provider.findRouteDetail(origin, requestedDestination, estimate);

        verify(odsayClient).searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                estimatedDestination.longitude(),
                estimatedDestination.latitude()
        );
        verify(odsayClient).searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                requestedDestination.longitude(),
                requestedDestination.latitude()
        );
    }

    @Test
    @DisplayName("mapObj 상세 경로 좌표가 있으면 정류장 좌표 대신 상세 좌표를 사용한다")
    void findRouteUsesDetailedRouteLineCoordinates() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint destination = point("가덕도 등대", "128.8294", "35.0241");
        when(odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        )).thenReturn(response());
        when(odsayClient.loadLane("0:0@2823889:1:66:103")).thenReturn(Optional.of(Map.of(
                "result", Map.of("lane", List.of(Map.of(
                        "section", List.of(Map.of("graphPos", List.of(
                                Map.of("x", "129.0403", "y", "35.1151"),
                                Map.of("x", "129.0100", "y", "35.1000"),
                                Map.of("x", "128.8300", "y", "35.0250")
                        )))
                )))
        )));
        when(walkingRouteProvider.findRoute(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        when(transitRealtimeProvider.adjustment(Mockito.any()))
                .thenReturn(TransitRealtimeAdjustment.none());

        TransitRouteResult result = provider.findRoute(origin, destination);

        assertThat(result.routeLines()).hasSize(2);
        assertThat(result.routeLines().get(0).lineName()).isEqualTo("1009");
        assertThat(result.routeLines().get(0).coordinatesJson())
                .contains("129.0100", "35.1000");
        assertThat(result.routeLines().get(0).fallbackUsed()).isFalse();
        assertThat(result.routeLines().get(1).mode()).isEqualTo("WALK");
        assertThat(result.routeLines().get(1).fallbackUsed()).isTrue();
        assertThat(result.routeLines().get(1).coordinatesJson()).contains("128.8294", "35.0241");
    }

    @Test
    @DisplayName("도보 경로 Provider 좌표가 있으면 WALK 경로선에 실제 도보 좌표를 사용한다")
    void findRouteUsesWalkingRouteProviderCoordinates() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint destination = point("가덕도 등대", "128.8294", "35.0241");
        when(odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        )).thenReturn(response());
        when(odsayClient.loadLane("0:0@2823889:1:66:103")).thenReturn(Optional.empty());
        when(walkingRouteProvider.findRoute(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.of(new WalkingRouteResult(
                        3,
                        200,
                        "[[128.8300,35.0250],[128.8298,35.0245],[128.8294,35.0241]]"
                )));
        when(transitRealtimeProvider.adjustment(Mockito.any()))
                .thenReturn(TransitRealtimeAdjustment.none());

        TransitRouteResult result = provider.findRoute(origin, destination);

        assertThat(result.routeLines()).hasSize(2);
        assertThat(result.routeLines().get(1).mode()).isEqualTo("WALK");
        assertThat(result.routeLines().get(1).coordinatesJson())
                .contains("128.8298", "35.0245");
        assertThat(result.routeLines().get(0).fallbackUsed()).isTrue();
        assertThat(result.routeLines().get(1).fallbackUsed()).isFalse();
    }

    @Test
    @DisplayName("경로 후보가 없으면 경로 없음 예외를 던진다")
    void findRouteThrowsWhenPathIsEmpty() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint destination = point("가덕도 등대", "128.8294", "35.0241");
        when(odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        )).thenReturn(Map.of("result", Map.of("path", List.of())));

        assertThatThrownBy(() -> provider.findRoute(origin, destination))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSIT_ROUTE_NOT_FOUND);
    }

    @Test
    @DisplayName("기본 최단 경로를 선택한 뒤 선택 경로에만 실시간 대기 보정을 적용한다")
    void findRouteAdjustsOnlySelectedFastestPath() {
        TransitPoint origin = point("부산역", "129.0403", "35.1151");
        TransitPoint destination = point("광안리", "129.1187", "35.1532");
        when(odsayClient.searchPublicTransitPath(
                origin.longitude(),
                origin.latitude(),
                destination.longitude(),
                destination.latitude()
        )).thenReturn(realtimeResponse());
        when(odsayClient.loadLane(Mockito.any())).thenReturn(Optional.empty());
        when(walkingRouteProvider.findRoute(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        when(transitRealtimeProvider.adjustment(Mockito.any()))
                .thenAnswer(invocation -> {
                    TransitRealtimeRequest request = invocation.getArgument(0);
                    if ("1001".equals(request.lineName())) {
                        return new TransitRealtimeAdjustment(20, "LONG_WAIT", "waitMinutes=27");
                    }
                    return TransitRealtimeAdjustment.none();
                });

        TransitRouteResult result = provider.findRoute(origin, destination);

        assertThat(result.totalMinutes()).isEqualTo(40);
        assertThat(result.segments().get(0).lineName()).isEqualTo("1001");
        assertThat(result.rawJson()).contains("\"totalTime\":20");
        verify(transitRealtimeProvider).adjustment(Mockito.argThat(request -> "1001".equals(request.lineName())));
    }

    private Map<String, Object> response() {
        return Map.of("result", Map.of("path", List.of(
                Map.of(
                        "info", Map.of("totalTime", 90, "payment", 1800),
                        "subPath", List.of(Map.of("trafficType", 3, "sectionTime", 5))
                ),
                Map.of(
                        "info", Map.of("totalTime", 42, "payment", 1550, "mapObj", "2823889:1:66:103"),
                        "subPath", List.of(
                                Map.of("trafficType", 3, "sectionTime", 6),
                                Map.of(
                                        "trafficType", 2,
                                        "lane", List.of(Map.of("busNo", "1009")),
                                        "startName", "부산역",
                                        "endName", "가덕도",
                                        "passStopList", Map.of("stations", List.of(
                                                Map.of("x", "129.0403", "y", "35.1151"),
                                                Map.of("x", "128.8300", "y", "35.0250")
                                        ))
                                ),
                                Map.of("trafficType", 3, "sectionTime", 7)
                        )
                )
        )));
    }

    private Map<String, Object> realtimeResponse() {
        return Map.of("result", Map.of("path", List.of(
                Map.of(
                        "info", Map.of("totalTime", 20, "payment", 1550, "mapObj", "1:1:1001"),
                        "subPath", List.of(Map.of(
                                "trafficType", 2,
                                "lane", List.of(Map.of("busNo", "1001")),
                                "startName", "부산역",
                                "startID", "12345",
                                "endName", "광안리",
                                "passStopList", Map.of("stations", List.of(
                                        Map.of("x", "129.0403", "y", "35.1151"),
                                        Map.of("x", "129.1187", "y", "35.1532")
                                ))
                        ))
                ),
                Map.of(
                        "info", Map.of("totalTime", 34, "payment", 1550, "mapObj", "1:1:40"),
                        "subPath", List.of(Map.of(
                                "trafficType", 2,
                                "lane", List.of(Map.of("busNo", "40")),
                                "startName", "부산역",
                                "startID", "12345",
                                "endName", "광안리",
                                "passStopList", Map.of("stations", List.of(
                                        Map.of("x", "129.0403", "y", "35.1151"),
                                        Map.of("x", "129.1187", "y", "35.1532")
                                ))
                        ))
                )
        )));
    }

    private TransitPoint point(String name, String longitude, String latitude) {
        return new TransitPoint(name, new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
