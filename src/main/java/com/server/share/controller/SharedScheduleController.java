package com.server.share.controller;

import com.server.schedule.dto.ScheduleMapResponse;
import com.server.share.dto.SharedScheduleResponse;
import com.server.share.service.ShareService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shared-schedules")
public class SharedScheduleController {

    private final ShareService shareService;

    public SharedScheduleController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}")
    public SharedScheduleResponse get(@PathVariable String token) {
        return shareService.getSharedSchedule(token);
    }

    @GetMapping("/{token}/map")
    public ScheduleMapResponse getMap(
            @PathVariable String token,
            @RequestParam(required = false) Integer dayNo
    ) {
        return shareService.getSharedMap(token, dayNo);
    }
}
