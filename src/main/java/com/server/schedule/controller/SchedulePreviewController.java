package com.server.schedule.controller;

import com.server.schedule.dto.SchedulePreviewCreateRequest;
import com.server.schedule.dto.SchedulePreviewResponse;
import com.server.schedule.service.SchedulePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Content;
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
    @Operation(
            summary = "일정 미리보기 생성",
            description = "입력을 정규화하고 일차별 가용시간을 계산해 Preview를 만든다. "
                    + "수정 가능한 충돌은 201 응답의 conflicts[]로 반환하고, 형식 오류만 400으로 반환한다. "
                    + "유효시간은 30분이며 Preview 하나로 일정 하나만 생성할 수 있다."
    )
    public SchedulePreviewResponse create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SchedulePreviewCreateRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "undecidedLodging",
                                            summary = "숙소 미정 1박 2일 (기본 생성 화면)",
                                            value = SchedulePreviewOpenApiExamples.UNDECIDED_LODGING
                                    ),
                                    @ExampleObject(
                                            name = "fixedLodging",
                                            summary = "고정 숙소 2박 3일, 종료 제약과 자유 요청 포함",
                                            value = SchedulePreviewOpenApiExamples.FIXED_LODGING
                                    )
                            }
                    )
            )
            @Valid @RequestBody SchedulePreviewCreateRequest request) {
        return previewService.create(request);
    }

    @GetMapping("/{previewId}")
    @Operation(
            summary = "일정 미리보기 조회",
            description = "새로고침 복원용이다. 만료된 Preview 는 410 PREVIEW_EXPIRED 로 응답한다."
    )
    public SchedulePreviewResponse get(
            @Parameter(description = "Preview 생성 응답의 previewId",
                    example = "6afa151a-3fb2-4f3d-821f-53228b88d1c0")
            @PathVariable UUID previewId) {
        return previewService.get(previewId);
    }
}
