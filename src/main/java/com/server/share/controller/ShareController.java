package com.server.share.controller;

import com.server.share.dto.ShareLinkCreateRequest;
import com.server.share.dto.ShareLinkResponse;
import com.server.share.service.ShareService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules/{scheduleId}/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkResponse create(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody ShareLinkCreateRequest request
    ) {
        return shareService.create(scheduleId, request);
    }

    @DeleteMapping("/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID scheduleId, @PathVariable UUID shareId) {
        shareService.revoke(scheduleId, shareId);
    }
}
