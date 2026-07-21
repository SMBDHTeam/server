package com.server.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_fixed_events")
public class ScheduleFixedEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_stop_id", nullable = false, unique = true)
    private ScheduleStop scheduleStop;

    @Column(name = "client_event_id", nullable = false)
    private String clientEventId;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ScheduleFixedEvent() {
    }

    public ScheduleFixedEvent(
            Schedule schedule,
            ScheduleStop scheduleStop,
            String clientEventId,
            String name,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt
    ) {
        this.id = UUID.randomUUID();
        this.schedule = schedule;
        this.scheduleStop = scheduleStop;
        this.clientEventId = clientEventId;
        this.name = name;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdAt = OffsetDateTime.now();
    }
}
