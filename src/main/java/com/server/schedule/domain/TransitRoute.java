package com.server.schedule.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "transit_routes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transit_routes_day_order",
                columnNames = {"schedule_day_id", "route_order"}
        )
)
public class TransitRoute {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @OneToOne
    @JoinColumn(name = "schedule_stop_id", unique = true)
    private ScheduleStop scheduleStop;

    @Column(name = "route_type", nullable = false)
    private String routeType;

    @Column(name = "route_order", nullable = false)
    private int routeOrder;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "fare_amount")
    private Integer fareAmount;

    @Column(nullable = false)
    private String provider = "UNKNOWN";

    @Column(name = "realtime_status", nullable = false)
    private String realtimeStatus = "UNAVAILABLE";

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "warnings_json", nullable = false, columnDefinition = "text")
    private String warningsJson = "[]";

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @OrderBy("segmentOrder ASC")
    @OneToMany(mappedBy = "transitRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransitSegment> segments = new ArrayList<>();

    @OrderBy("lineOrder ASC")
    @OneToMany(mappedBy = "transitRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransitRouteLine> routeLines = new ArrayList<>();

    protected TransitRoute() {
    }

    public TransitRoute(
            ScheduleDay scheduleDay,
            ScheduleStop scheduleStop,
            String routeType,
            int routeOrder,
            int totalMinutes,
            Integer fareAmount,
            String rawJson
    ) {
        this(scheduleDay, scheduleStop, routeType, routeOrder, totalMinutes, fareAmount,
                "UNKNOWN", "UNAVAILABLE", false, "[]", rawJson);
    }

    public TransitRoute(
            ScheduleDay scheduleDay,
            ScheduleStop scheduleStop,
            String routeType,
            int routeOrder,
            int totalMinutes,
            Integer fareAmount,
            String provider,
            String realtimeStatus,
            boolean fallbackUsed,
            String warningsJson,
            String rawJson
    ) {
        this.id = UUID.randomUUID();
        this.scheduleDay = scheduleDay;
        this.scheduleStop = scheduleStop;
        this.routeType = routeType;
        this.routeOrder = routeOrder;
        this.totalMinutes = totalMinutes;
        this.fareAmount = fareAmount;
        this.provider = provider == null || provider.isBlank() ? "UNKNOWN" : provider;
        this.realtimeStatus = realtimeStatus == null || realtimeStatus.isBlank() ? "UNAVAILABLE" : realtimeStatus;
        this.fallbackUsed = fallbackUsed;
        this.warningsJson = warningsJson == null || warningsJson.isBlank() ? "[]" : warningsJson;
        this.rawJson = rawJson;
        scheduleDay.addTransitRoute(this);
        if (scheduleStop != null) {
            scheduleStop.setInboundTransit(this);
        }
    }

    public void addSegment(TransitSegment segment) {
        this.segments.add(segment);
    }

    public void addRouteLine(TransitRouteLine routeLine) {
        this.routeLines.add(routeLine);
    }

    public UUID getId() {
        return id;
    }

    public String getRouteType() {
        return routeType;
    }

    public ScheduleStop getScheduleStop() {
        return scheduleStop;
    }

    public int getRouteOrder() {
        return routeOrder;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public Integer getFareAmount() {
        return fareAmount;
    }

    public String getProvider() {
        return provider;
    }

    public String getRealtimeStatus() {
        return realtimeStatus;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public List<TransitSegment> getSegments() {
        return segments;
    }

    public List<TransitRouteLine> getRouteLines() {
        return routeLines;
    }
}
