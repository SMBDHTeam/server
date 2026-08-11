package com.server.schedule.controller;

import com.server.schedule.dto.ScheduleCreateRequest;
import com.server.schedule.dto.ScheduleSummaryListResponse;
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
    @Operation(
            summary = "일정 생성 (V1 호환)",
            description = "기존 클라이언트 호환용이다. 신규 연동은 Idempotency-Key 를 붙인 V2 요청을 쓴다.",
            hidden = true
    )
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
    @Operation(
            summary = "일정 생성",
            description = "POST /api/v1/schedule-previews 로 만든 Preview 를 소비해 일정을 만든다. "
                    + "Preview 하나로 일정 하나만 만들 수 있고, 이미 소비된 Preview 는 409 로 응답한다. "
                    + "같은 Idempotency-Key 로 다시 요청하면 새 일정을 만들지 않는다."
    )
    public ScheduleResponse createFromPreview(
            @Parameter(
                    description = "재요청 시 중복 생성을 막는 키. 화면에서 생성 버튼을 누를 때 UUID 를 만들어 "
                            + "재시도까지 같은 값을 쓴다. 최대 128자.",
                    required = true,
                    example = "3f1b9c2e-6a4d-4f77-9b2a-1c8d5e0f4a31"
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SchedulePreviewScheduleRequest.class),
                            examples = @ExampleObject(
                                    name = "fromPreview",
                                    summary = "Preview 응답의 previewId 를 그대로 전달",
                                    value = "{\"previewId\": \"6afa151a-3fb2-4f3d-821f-53228b88d1c0\"}"
                            )
                    )
            )
            @Valid @RequestBody SchedulePreviewScheduleRequest request
    ) {
        if (scheduleV2Service == null) {
            throw new IllegalStateException("Schedule V2 service is not configured");
        }
        return scheduleV2Service.create(request, idempotencyKey);
    }

    @GetMapping
    @Operation(
            summary = "일정 목록 조회",
            description = "목록 카드에 필요한 축약 정보만 반환한다. 방문지·경로·평가는 단건 조회를 사용한다."
    )
    public ScheduleSummaryListResponse getAll() {
        return scheduleService.getAll();
    }

    @GetMapping("/{scheduleId}")
    @Operation(
            summary = "일정 단건 조회",
            description = "방문지·경로·planningAssumptions 를 포함한 전체 일정을 반환한다. "
                    + "생성 시점에만 계산하는 evaluation 은 포함하지 않는다."
    )
    public ScheduleResponse get(
            @Parameter(description = "일정 생성 응답의 id",
                    example = "f2536c52-69d1-4e6c-8ab6-2ede45dba2cd")
            @PathVariable UUID scheduleId) {
        return scheduleService.get(scheduleId);
    }

    @PatchMapping("/{scheduleId}")
    @Operation(summary = "일정 수정", description = "stopId는 생성 응답 값으로, 새 장소를 추가할 때는 placeId로 교체해야 합니다.")
    public ScheduleResponse update(
            @Parameter(description = "일정 생성 응답의 id",
                    example = "f2536c52-69d1-4e6c-8ab6-2ede45dba2cd")
            @PathVariable UUID scheduleId,
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
            @Parameter(description = "일정 생성 응답의 id",
                    example = "f2536c52-69d1-4e6c-8ab6-2ede45dba2cd")
            @PathVariable UUID scheduleId,
            @Parameter(description = "조회할 일차. 생략하면 전체 일차의 마커와 경로선을 함께 반환한다.",
                    example = "1")
            @RequestParam(required = false) Integer dayNo
    ) {
        return scheduleService.getMap(scheduleId, dayNo);
    }
}
