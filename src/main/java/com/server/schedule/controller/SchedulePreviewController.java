package com.server.schedule.controller;

import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import com.server.schedule.service.SchedulePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule-previews")
@Tag(name = "일정 미리보기", description = "Planner 실행 전 일정 조건 계산과 확인")
public class SchedulePreviewController {

    private final SchedulePreviewService previewService;

    public SchedulePreviewController(SchedulePreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "일정 미리보기 생성")
    public SchedulePreviewResponse create(@Valid @RequestBody SchedulePreviewCreateRequest request) {
        return previewService.create(request);
    }

    @GetMapping("/{previewId}")
    @Operation(summary = "일정 미리보기 조회")
    public SchedulePreviewResponse get(@PathVariable UUID previewId) {
        return previewService.get(previewId);
    }
}
