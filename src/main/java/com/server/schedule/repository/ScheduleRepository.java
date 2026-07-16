package com.server.schedule.repository;

import com.server.schedule.domain.Schedule;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findAllByOrderByStartDateAscCreatedAtDesc();

    @Query("select schedule.id from Schedule schedule where schedule.preview.id = :previewId")
    Optional<UUID> findIdByPreviewId(UUID previewId);
}
