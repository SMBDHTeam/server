package com.server.schedule.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "schedules")
public class Schedule {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "daily_start_time", nullable = false)
    private LocalTime dailyStartTime;

    @Column(name = "daily_end_time", nullable = false)
    private LocalTime dailyEndTime;

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

    @Column(name = "style_summary", columnDefinition = "text")
    private String styleSummary;

    @Column(name = "condition_json", nullable = false, columnDefinition = "text")
    private String conditionJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OrderBy("dayNo ASC")
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleDay> days = new ArrayList<>();

    protected Schedule() {
    }

    public Schedule(
            LocalDate startDate,
            LocalDate endDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            String startPlaceName,
            BigDecimal startLongitude,
            BigDecimal startLatitude,
            String endPlaceName,
            BigDecimal endLongitude,
            BigDecimal endLatitude,
            String styleSummary,
            String conditionJson
    ) {
        this.id = UUID.randomUUID();
        this.status = "CONFIRMED";
        this.startDate = startDate;
        this.endDate = endDate;
        this.dailyStartTime = dailyStartTime;
        this.dailyEndTime = dailyEndTime;
        this.startPlaceName = startPlaceName;
        this.startLongitude = startLongitude;
        this.startLatitude = startLatitude;
        this.endPlaceName = endPlaceName;
        this.endLongitude = endLongitude;
        this.endLatitude = endLatitude;
        this.styleSummary = styleSummary;
        this.conditionJson = conditionJson;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void addDay(ScheduleDay day) {
        this.days.add(day);
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getStyleSummary() {
        return styleSummary;
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

    public List<ScheduleDay> getDays() {
        return days;
    }
}
