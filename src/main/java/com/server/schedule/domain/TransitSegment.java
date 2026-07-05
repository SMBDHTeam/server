package com.server.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @ManyToOne
    @JoinColumn(name = "transit_route_id", nullable = false)
    private TransitRoute transitRoute;

    @Column(name = "segment_order", nullable = false)
    private int segmentOrder;

    @Column(nullable = false)
    private String mode;

    @Column(name = "line_name")
    private String lineName;

    @Column(name = "start_station_name")
    private String startStationName;

    @Column(name = "end_station_name")
    private String endStationName;

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
        this.id = UUID.randomUUID();
        this.transitRoute = transitRoute;
        this.segmentOrder = segmentOrder;
        this.mode = mode;
        this.lineName = lineName;
        this.startStationName = startStationName;
        this.endStationName = endStationName;
        transitRoute.addSegment(this);
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

    public String getStartStationName() {
        return startStationName;
    }

    public String getEndStationName() {
        return endStationName;
    }
}
