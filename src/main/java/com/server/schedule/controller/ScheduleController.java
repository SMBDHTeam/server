package com.server.schedule.controller;

import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleListResponse;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import com.server.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@Valid @RequestBody ScheduleCreateRequest request) {
        return scheduleService.create(request);
    }

    @GetMapping
    public ScheduleListResponse getAll() {
        return scheduleService.getAll();
    }

    @PatchMapping("/{scheduleId}")
    public ScheduleResponse update(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody ScheduleUpdateRequest request
    ) {
        return scheduleService.update(scheduleId, request);
    }

    @GetMapping("/{scheduleId}/map")
    public ScheduleMapResponse getMap(
            @PathVariable UUID scheduleId,
            @RequestParam(required = false) Integer dayNo
    ) {
        return scheduleService.getMap(scheduleId, dayNo);
    }
}
