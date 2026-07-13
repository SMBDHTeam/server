package com.server.schedule.domain;

import com.server.place.domain.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "schedule_stops",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_schedule_stops_day_order",
                columnNames = {"schedule_day_id", "stop_order"}
        )
)
public class ScheduleStop {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Column(name = "stay_minutes", nullable = false)
    private int stayMinutes;

    @Column(name = "selection_reasons_json", nullable = false, columnDefinition = "text")
    private String selectionReasonsJson = "[]";

    @Column(name = "warnings_json", nullable = false, columnDefinition = "text")
    private String warningsJson = "[]";

    @OneToOne(mappedBy = "scheduleStop", fetch = FetchType.LAZY)
    private TransitRoute inboundTransit;

    protected ScheduleStop() {
    }

    public ScheduleStop(ScheduleDay scheduleDay, Place place, int stopOrder, int stayMinutes) {
        this.id = UUID.randomUUID();
        this.scheduleDay = scheduleDay;
        this.place = place;
        this.stopOrder = stopOrder;
        this.stayMinutes = stayMinutes;
        scheduleDay.addStop(this);
    }

    public void updateDeliveryInfo(String selectionReasonsJson, String warningsJson) {
        this.selectionReasonsJson = selectionReasonsJson == null || selectionReasonsJson.isBlank()
                ? "[]"
                : selectionReasonsJson;
        this.warningsJson = warningsJson == null || warningsJson.isBlank()
                ? "[]"
                : warningsJson;
    }

    public void updateStayMinutes(int stayMinutes) {
        if (stayMinutes <= 0) {
            throw new IllegalArgumentException("stayMinutes must be positive");
        }
        this.stayMinutes = stayMinutes;
    }

    public void reassign(ScheduleDay scheduleDay, int stopOrder, int stayMinutes) {
        if (scheduleDay == null || stopOrder <= 0 || stayMinutes <= 0) {
            throw new IllegalArgumentException("scheduleDay, stopOrder and stayMinutes must be valid");
        }
        this.scheduleDay = scheduleDay;
        this.stopOrder = stopOrder;
        this.stayMinutes = stayMinutes;
    }

    public void setInboundTransit(TransitRoute inboundTransit) {
        this.inboundTransit = inboundTransit;
    }

    public UUID getId() {
        return id;
    }

    public Place getPlace() {
        return place;
    }

    public int getStopOrder() {
        return stopOrder;
    }

    public int getStayMinutes() {
        return stayMinutes;
    }

    public String getSelectionReasonsJson() {
        return selectionReasonsJson;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public TransitRoute getInboundTransit() {
        return inboundTransit;
    }
}
