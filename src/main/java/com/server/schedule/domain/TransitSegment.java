package com.server.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "transit_segments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transit_segments_route_order",
                columnNames = {"transit_route_id", "segment_order"}
        )
)
public class TransitSegment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transit_route_id", nullable = false)
    private TransitRoute transitRoute;

    @Column(name = "segment_order", nullable = false)
    private int segmentOrder;

    @Column(nullable = false)
    private String mode;

    @Column(name = "line_name")
    private String lineName;

    @Column(name = "start_station_id")
    private String startStationId;

    @Column(name = "start_station_name")
    private String startStationName;

    @Column(name = "end_station_id")
    private String endStationId;

    @Column(name = "end_station_name")
    private String endStationName;

    @Column(name = "instruction", nullable = false, columnDefinition = "text")
    private String instruction;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "station_count")
    private Integer stationCount;

    @Column(name = "wait_minutes", nullable = false)
    private int waitMinutes;

    @Column(name = "realtime_status", nullable = false)
    private String realtimeStatus = "UNAVAILABLE";

    protected TransitSegment() {
    }

    public TransitSegment(
            TransitRoute transitRoute,
            int segmentOrder,
            String mode,
            String lineName,
            String startStationName,
            String endStationName
    ) {
        this(transitRoute, segmentOrder, mode, lineName, null, startStationName, null, endStationName,
                null, 0, null, null, 0, "UNAVAILABLE");
    }

    public TransitSegment(
            TransitRoute transitRoute,
            int segmentOrder,
            String mode,
            String lineName,
            String startStationId,
            String startStationName,
            String endStationId,
            String endStationName,
            String instruction,
            int durationMinutes,
            Integer distanceMeters,
            Integer stationCount,
            int waitMinutes,
            String realtimeStatus
    ) {
        this.id = UUID.randomUUID();
        this.transitRoute = transitRoute;
        this.segmentOrder = segmentOrder;
        this.mode = mode;
        this.lineName = lineName;
        this.startStationId = startStationId;
        this.startStationName = startStationName;
        this.endStationId = endStationId;
        this.endStationName = endStationName;
        this.instruction = instruction == null || instruction.isBlank()
                ? defaultInstruction(mode, lineName, startStationName, endStationName)
                : instruction;
        this.durationMinutes = Math.max(0, durationMinutes);
        this.distanceMeters = distanceMeters;
        this.stationCount = stationCount;
        this.waitMinutes = Math.max(0, waitMinutes);
        this.realtimeStatus = realtimeStatus == null || realtimeStatus.isBlank() ? "UNAVAILABLE" : realtimeStatus;
        transitRoute.addSegment(this);
    }

    private String defaultInstruction(String mode, String lineName, String startStationName, String endStationName) {
        String start = startStationName == null || startStationName.isBlank() ? "출발지" : startStationName;
        String end = endStationName == null || endStationName.isBlank() ? "도착지" : endStationName;
        if ("WALK".equals(mode)) {
            return start + "에서 " + end + "까지 도보 이동";
        }
        String line = lineName == null || lineName.isBlank() ? mode : lineName;
        return start + "에서 " + line + " 승차 후 " + end + "에서 하차";
    }

    public String getMode() {
        return mode;
    }

    public int getSegmentOrder() {
        return segmentOrder;
    }

    public String getLineName() {
        return lineName;
    }

    public String getStartStationId() {
        return startStationId;
    }

    public String getStartStationName() {
        return startStationName;
    }

    public String getEndStationId() {
        return endStationId;
    }

    public String getEndStationName() {
        return endStationName;
    }

    public String getInstruction() {
        return instruction;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Integer getDistanceMeters() {
        return distanceMeters;
    }

    public Integer getStationCount() {
        return stationCount;
    }

    public int getWaitMinutes() {
        return waitMinutes;
    }

    public String getRealtimeStatus() {
        return realtimeStatus;
    }
}
