package com.server.schedule.repository;

import com.server.schedule.domain.Schedule;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findAllByOrderByStartDateAscCreatedAtDesc();

    @Override
    @EntityGraph(attributePaths = {
            "days",
            "days.stops",
            "days.stops.place",
            "days.stops.place.operatingInfo",
            "days.stops.inboundTransit",
            "days.transitRoutes",
            "days.transitRoutes.segments",
            "days.transitRoutes.routeLines"
    })
    Optional<Schedule> findById(UUID id);

    @Query("select schedule.id from Schedule schedule where schedule.preview.id = :previewId")
    Optional<UUID> findIdByPreviewId(UUID previewId);
}
