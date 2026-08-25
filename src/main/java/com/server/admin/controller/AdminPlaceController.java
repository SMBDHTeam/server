package com.server.admin.controller;

import com.server.admin.dto.AdminIngestionStatusResponse;
import com.server.admin.dto.AdminPlaceResponse;
import com.server.admin.dto.PlaceHiddenUpdateRequest;
import com.server.admin.service.AdminPlaceService;
import com.server.place.ingestion.TourApiPlaceIngestionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/places")
@Tag(name = "관리자 - 장소", description = "적재 상태 조회, 수동 적재, 장소 숨김")
public class AdminPlaceController {

    private final AdminPlaceService adminPlaceService;

    public AdminPlaceController(AdminPlaceService adminPlaceService) {
        this.adminPlaceService = adminPlaceService;
    }

    @GetMapping("/ingestion")
    @Operation(
            summary = "적재 상태와 남은 예산",
            description = "ingestion_status 별 장소 수와 오늘(KST) 남은 TourAPI 호출 수를 준다. "
                    + "수동 적재 전에 여유가 있는지 확인한다."
    )
    public AdminIngestionStatusResponse getIngestionStatus() {
        return adminPlaceService.getIngestionStatus();
    }

    @PostMapping("/ingestion")
    @Operation(
            summary = "수동 적재 실행",
            description = "스케줄러와 같은 하루 예산을 쓴다. 남은 양이 없으면 429 로 거절한다. "
                    + "시작해 봐야 예약 단계에서 막혀 아무것도 하지 못하기 때문이다. "
                    + "다른 적재가 진행 중이면 lockSkipped 가 true 로 돌아온다."
    )
    public TourApiPlaceIngestionResult runIngestion() {
        return adminPlaceService.runIngestion();
    }

    @GetMapping("/hidden")
    @Operation(summary = "가려 둔 장소 목록", description = "최근에 가린 순이다.")
    public List<AdminPlaceResponse> getHiddenPlaces() {
        return adminPlaceService.getHiddenPlaces();
    }

    @PatchMapping("/{placeId}/hidden")
    @Operation(
            summary = "장소 숨김·해제",
            description = "지우지 않고 가린다. 행을 지우면 TourAPI 증분 동기화가 다음 실행에서 "
                    + "같은 장소를 다시 만든다. 가린 장소는 검색과 상세 조회에서 빠진다."
    )
    public AdminPlaceResponse updateHidden(
            @Parameter(example = "42") @PathVariable Long placeId,
            @Valid @RequestBody PlaceHiddenUpdateRequest request
    ) {
        return adminPlaceService.updateHidden(placeId, request.hidden(), request.reason());
    }
}
