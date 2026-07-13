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
        name = "transit_route_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transit_route_lines_route_order",
                columnNames = {"transit_route_id", "line_order"}
        )
)
public class TransitRouteLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transit_route_id", nullable = false)
    private TransitRoute transitRoute;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @Column(nullable = false)
    private String mode;

    @Column(name = "line_name")
    private String lineName;

    @Column(name = "coordinates_json", nullable = false, columnDefinition = "text")
    private String coordinatesJson;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "instruction", columnDefinition = "text")
    private String instruction;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    protected TransitRouteLine() {
    }

    public TransitRouteLine(
            TransitRoute transitRoute,
            int lineOrder,
            String mode,
            String lineName,
            String coordinatesJson
    ) {
        this(transitRoute, lineOrder, mode, lineName, coordinatesJson, null, null, null, false);
    }

    public TransitRouteLine(
            TransitRoute transitRoute,
            int lineOrder,
            String mode,
            String lineName,
            String coordinatesJson,
            Integer durationMinutes,
            Integer distanceMeters,
            String instruction,
            boolean fallbackUsed
    ) {
        this.id = UUID.randomUUID();
        this.transitRoute = transitRoute;
        this.lineOrder = lineOrder;
        this.mode = mode;
        this.lineName = lineName;
        this.coordinatesJson = coordinatesJson;
        this.durationMinutes = durationMinutes;
        this.distanceMeters = distanceMeters;
        this.instruction = instruction;
        this.fallbackUsed = fallbackUsed;
        transitRoute.addRouteLine(this);
    }

    public int getLineOrder() {
        return lineOrder;
    }

    public String getMode() {
        return mode;
    }

    public String getLineName() {
        return lineName;
    }

    public String getCoordinatesJson() {
        return coordinatesJson;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public Integer getDistanceMeters() {
        return distanceMeters;
    }

    public String getInstruction() {
        return instruction;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }
}
