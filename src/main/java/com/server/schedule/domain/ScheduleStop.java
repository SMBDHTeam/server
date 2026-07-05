package com.server.schedule.domain;

import com.server.place.domain.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @ManyToOne
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @ManyToOne
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    @Column(name = "stay_minutes", nullable = false)
    private int stayMinutes;

    @OneToOne(mappedBy = "scheduleStop")
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

    public TransitRoute getInboundTransit() {
        return inboundTransit;
    }
}
