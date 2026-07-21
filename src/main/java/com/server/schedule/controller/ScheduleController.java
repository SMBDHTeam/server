package com.server.schedule.controller;

import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleListResponse;
import com.server.schedule.dto.ScheduleMapResponse;
import com.server.schedule.dto.ScheduleResponse;
import com.server.schedule.dto.ScheduleUpdateRequest;
import com.server.schedule.dto.SchedulePreviewScheduleRequest;
import com.server.schedule.service.ScheduleService;
import com.server.schedule.service.ScheduleV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
@Tag(name = "일정", description = "일정 생성, 조회, 수정과 지도 데이터")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleV2Service scheduleV2Service;

    public ScheduleController(ScheduleService scheduleService) {
        this(scheduleService, null);
    }

    @Autowired
    public ScheduleController(ScheduleService scheduleService, ScheduleV2Service scheduleV2Service) {
        this.scheduleService = scheduleService;
        this.scheduleV2Service = scheduleV2Service;
    }

    @PostMapping(headers = "!Idempotency-Key")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "일정 생성")
    public ScheduleResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScheduleCreateRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "oneDay",
                                            summary = "로컬 seed 기반 1일 일정",
                                            value = ScheduleOpenApiExamples.ONE_DAY_CREATE
                                    ),
                                    @ExampleObject(
                                            name = "fourDay",
                                            summary = "현재 로컬 장소 ID가 연결된 3박 4일 일정",
                                            value = ScheduleOpenApiExamples.FOUR_DAY_CREATE
                                    )
                            }
                    )
            )
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        return scheduleService.create(request);
    }

    @PostMapping(headers = "Idempotency-Key")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Preview 기반 일정 생성")
    public ScheduleResponse createFromPreview(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SchedulePreviewScheduleRequest request
    ) {
        if (scheduleV2Service == null) {
            throw new IllegalStateException("Schedule V2 service is not configured");
        }
        return scheduleV2Service.create(request, idempotencyKey);
    }

    @GetMapping
    @Operation(summary = "전체 일정 조회")
    public ScheduleListResponse getAll() {
        return scheduleService.getAll();
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "일정 단건 조회")
    public ScheduleResponse get(@PathVariable UUID scheduleId) {
        return scheduleService.get(scheduleId);
    }

    @PatchMapping("/{scheduleId}")
    @Operation(summary = "일정 수정", description = "stopId는 생성 응답 값으로, 새 장소를 추가할 때는 placeId로 교체해야 합니다.")
    public ScheduleResponse update(
            @Parameter(description = "일정 ID") @PathVariable UUID scheduleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScheduleUpdateRequest.class),
                            examples = @ExampleObject(
                                    name = "reorderAndAddPlace",
                                    summary = "기존 방문지 유지와 새 장소 추가",
                                    value = ScheduleOpenApiExamples.UPDATE
                            )
                    )
            )
            @Valid @RequestBody ScheduleUpdateRequest request
    ) {
        return scheduleService.update(scheduleId, request);
    }

    @GetMapping("/{scheduleId}/map")
    @Operation(summary = "일정 지도 조회")
    public ScheduleMapResponse getMap(
            @Parameter(description = "일정 ID") @PathVariable UUID scheduleId,
            @Parameter(description = "조회할 일차", example = "1")
            @RequestParam(required = false) Integer dayNo
    ) {
        return scheduleService.getMap(scheduleId, dayNo);
    }
}
