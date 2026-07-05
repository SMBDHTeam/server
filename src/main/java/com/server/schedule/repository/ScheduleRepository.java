package com.server.schedule.repository;

import com.server.schedule.domain.Schedule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
}
