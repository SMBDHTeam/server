package com.server.admin.controller;

import com.server.admin.domain.AdminActionTargetType;
import com.server.admin.dto.AdminActionListResponse;
import com.server.admin.service.AdminActionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/actions")
@Tag(name = "관리자 - 조치 이력", description = "누가 언제 무엇을 왜 했는지")
public class AdminActionController {

    private final AdminActionQueryService adminActionQueryService;

    public AdminActionController(AdminActionQueryService adminActionQueryService) {
        this.adminActionQueryService = adminActionQueryService;
    }

    @GetMapping
    @Operation(
            summary = "조치 이력 조회",
            description = "최근 조치부터 준다. targetType 만 주면 그 종류 전부를, targetId 까지 "
                    + "주면 그 대상의 이력만 본다. 기록은 수정하거나 지울 수 없다."
    )
    public AdminActionListResponse getActions(
            @Parameter(description = "USER, POST, COMMENT, REPORT, PLACE, SYSTEM")
            @RequestParam(required = false) AdminActionTargetType targetType,

            @Parameter(description = "targetType 과 함께 줘야 걸린다", example = "5")
            @RequestParam(required = false) Long targetId,

            @Parameter(description = "0부터 시작한다", example = "0")
            @RequestParam(required = false) Integer page,

            @Parameter(description = "기본 20, 최대 100", example = "20")
            @RequestParam(required = false) Integer size
    ) {
        return adminActionQueryService.getActions(targetType, targetId, page, size);
    }
}
