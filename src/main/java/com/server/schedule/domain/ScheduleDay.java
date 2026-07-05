package com.server.schedule.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "schedule_days",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_schedule_days_schedule_day_no",
                columnNames = {"schedule_id", "day_no"}
        )
)
public class ScheduleDay {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(nullable = false)
    private LocalDate date;

    @OrderBy("stopOrder ASC")
    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleStop> stops = new ArrayList<>();

    @OrderBy("routeOrder ASC")
    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransitRoute> transitRoutes = new ArrayList<>();

    protected ScheduleDay() {
    }

    public ScheduleDay(Schedule schedule, int dayNo, LocalDate date) {
        this.id = UUID.randomUUID();
        this.schedule = schedule;
        this.dayNo = dayNo;
        this.date = date;
        schedule.addDay(this);
    }

    public void addStop(ScheduleStop stop) {
        this.stops.add(stop);
    }

    public void addTransitRoute(TransitRoute transitRoute) {
        this.transitRoutes.add(transitRoute);
    }

    public UUID getId() {
        return id;
    }

    public int getDayNo() {
        return dayNo;
    }

    public LocalDate getDate() {
        return date;
    }

    public List<ScheduleStop> getStops() {
        return stops;
    }

    public List<TransitRoute> getTransitRoutes() {
        return transitRoutes;
    }
}
