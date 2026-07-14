package com.server.share.controller;

import com.server.schedule.dto.ScheduleMapResponse;
import com.server.share.dto.SharedScheduleResponse;
import com.server.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shared-schedules")
@Tag(name = "공유 일정", description = "공유 토큰으로 읽기 전용 일정과 지도 조회")
public class SharedScheduleController {

    private final ShareService shareService;

    public SharedScheduleController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "공유 일정 조회")
    public SharedScheduleResponse get(
            @Parameter(description = "공유 링크 생성 응답의 token") @PathVariable String token
    ) {
        return shareService.getSharedSchedule(token);
    }

    @GetMapping("/{token}/map")
    @Operation(summary = "공유 일정 지도 조회")
    public ScheduleMapResponse getMap(
            @Parameter(description = "공유 링크 생성 응답의 token") @PathVariable String token,
            @Parameter(description = "조회할 일차", example = "1")
            @RequestParam(required = false) Integer dayNo
    ) {
        return shareService.getSharedMap(token, dayNo);
    }
}
