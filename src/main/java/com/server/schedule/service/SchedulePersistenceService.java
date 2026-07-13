package com.server.schedule.service;

import com.server.schedule.domain.Schedule;
import com.server.schedule.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulePersistenceService {

    private final ScheduleRepository scheduleRepository;

    public SchedulePersistenceService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    @Transactional
    public Schedule save(Schedule schedule) {
        return scheduleRepository.save(schedule);
    }
}
