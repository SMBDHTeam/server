package com.server.share.controller;

import com.server.share.dto.ShareLinkCreateRequest;
import com.server.share.dto.ShareLinkResponse;
import com.server.share.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "일정 공유", description = "읽기 전용 공유 링크 생성과 폐기")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "공유 링크 생성")
    public ShareLinkResponse create(
            @Parameter(description = "일정 ID") @PathVariable UUID scheduleId,
            @Valid @RequestBody ShareLinkCreateRequest request
    ) {
        return shareService.create(scheduleId, request);
    }

    @DeleteMapping("/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "공유 링크 폐기")
    public void revoke(
            @Parameter(description = "일정 ID") @PathVariable UUID scheduleId,
            @Parameter(description = "공유 링크 ID") @PathVariable UUID shareId
    ) {
        shareService.revoke(scheduleId, shareId);
    }
}
