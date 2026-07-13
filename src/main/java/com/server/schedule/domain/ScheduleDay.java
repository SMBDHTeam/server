package com.server.schedule.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "day_no", nullable = false)
    private int dayNo;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "start_place_name", nullable = false)
    private String startPlaceName;

    @Column(name = "start_longitude", nullable = false, precision = 12, scale = 8)
    private BigDecimal startLongitude;

    @Column(name = "start_latitude", nullable = false, precision = 12, scale = 8)
    private BigDecimal startLatitude;

    @Column(name = "end_place_name", nullable = false)
    private String endPlaceName;

    @Column(name = "end_longitude", nullable = false, precision = 12, scale = 8)
    private BigDecimal endLongitude;

    @Column(name = "end_latitude", nullable = false, precision = 12, scale = 8)
    private BigDecimal endLatitude;

    @OrderBy("stopOrder ASC")
    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleStop> stops = new ArrayList<>();

    @OrderBy("routeOrder ASC")
    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransitRoute> transitRoutes = new ArrayList<>();

    protected ScheduleDay() {
    }

    public ScheduleDay(Schedule schedule, int dayNo, LocalDate date) {
        this(
                schedule,
                dayNo,
                date,
                schedule.getDailyStartTime(),
                schedule.getDailyEndTime(),
                schedule.getStartPlaceName(),
                schedule.getStartLongitude(),
                schedule.getStartLatitude(),
                schedule.getEndPlaceName(),
                schedule.getEndLongitude(),
                schedule.getEndLatitude()
        );
    }

    public ScheduleDay(
            Schedule schedule,
            int dayNo,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String startPlaceName,
            BigDecimal startLongitude,
            BigDecimal startLatitude,
            String endPlaceName,
            BigDecimal endLongitude,
            BigDecimal endLatitude
    ) {
        this.id = UUID.randomUUID();
        this.schedule = schedule;
        this.dayNo = dayNo;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startPlaceName = startPlaceName;
        this.startLongitude = startLongitude;
        this.startLatitude = startLatitude;
        this.endPlaceName = endPlaceName;
        this.endLongitude = endLongitude;
        this.endLatitude = endLatitude;
        schedule.addDay(this);
    }

    public void addStop(ScheduleStop stop) {
        this.stops.add(stop);
    }

    public void removeStop(ScheduleStop stop) {
        this.stops.remove(stop);
    }

    public void sortStops() {
        this.stops.sort(java.util.Comparator.comparingInt(ScheduleStop::getStopOrder));
    }

    public void addTransitRoute(TransitRoute transitRoute) {
        this.transitRoutes.add(transitRoute);
    }

    public void clearTransitRoutes() {
        this.transitRoutes.forEach(route -> {
            if (route.getScheduleStop() != null) {
                route.getScheduleStop().setInboundTransit(null);
            }
        });
        this.transitRoutes.clear();
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

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getStartPlaceName() {
        return startPlaceName;
    }

    public BigDecimal getStartLongitude() {
        return startLongitude;
    }

    public BigDecimal getStartLatitude() {
        return startLatitude;
    }

    public String getEndPlaceName() {
        return endPlaceName;
    }

    public BigDecimal getEndLongitude() {
        return endLongitude;
    }

    public BigDecimal getEndLatitude() {
        return endLatitude;
    }

    public List<ScheduleStop> getStops() {
        return stops;
    }

    public List<TransitRoute> getTransitRoutes() {
        return transitRoutes;
    }
}
