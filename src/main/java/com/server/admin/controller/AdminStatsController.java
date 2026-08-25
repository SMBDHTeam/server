package com.server.admin.controller;

import com.server.admin.dto.AdminStatsPopularResponse;
import com.server.admin.dto.AdminStatsSummaryResponse;
import com.server.admin.dto.AdminStatsTrendResponse;
import com.server.admin.dto.StatsMetric;
import com.server.admin.service.AdminStatsService;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stats")
@Tag(name = "관리자 - 통계", description = "총계, 일자별 추이, 인기 장소·해시태그")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "총계와 기간 증감",
            description = "가입자·게시물·일정의 누적과 최근 기간 증가분을 준다. "
                    + "대기 중인 신고 수와 정지 사용자 수도 함께 준다. days 기본값은 7이다."
    )
    public AdminStatsSummaryResponse getSummary(
            @Parameter(description = "집계 기간(일). 최대 365", example = "7")
            @RequestParam(required = false) Integer days) {
        return adminStatsService.getSummary(days);
    }

    @GetMapping("/trend")
    @Operation(
            summary = "일자별 추이",
            description = "값이 0인 날도 포함한다. 빈 날을 건너뛰면 그래프가 실제보다 완만해 보인다."
    )
    public AdminStatsTrendResponse getTrend(
            @Parameter(description = "USERS, POSTS, SCHEDULES", example = "POSTS")
            @RequestParam StatsMetric metric,
            @Parameter(description = "집계 기간(일). 최대 365", example = "30")
            @RequestParam(required = false) Integer days) {
        return adminStatsService.getTrend(metric, days);
    }

    @GetMapping("/popular")
    @Operation(
            summary = "인기 장소·해시태그",
            description = "장소는 게시물에 태그된 횟수로 센다. 일정에 담긴 횟수를 쓰면 "
                    + "Planner 가 고른 것이 섞여 사용자가 고른 것과 구분되지 않는다. "
                    + "해시태그는 집계 컬럼 대신 실제 연결을 센다."
    )
    public AdminStatsPopularResponse getPopular(
            @Parameter(description = "PLACE 또는 HASHTAG", example = "PLACE")
            @RequestParam String type,
            @Parameter(example = "10") @RequestParam(required = false) Integer size) {
        return switch (type == null ? "" : type.toUpperCase()) {
            case "PLACE" -> adminStatsService.getPopularPlaces(size);
            case "HASHTAG" -> adminStatsService.getPopularHashtags(size);
            default -> throw new BusinessException(ErrorCode.INVALID_STATS_TYPE);
        };
    }
}
