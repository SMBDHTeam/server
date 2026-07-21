package com.server.facility.controller;

import com.server.facility.dto.NearbyFacilityResponse;
import com.server.facility.service.NearbyFacilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/places")
@Tag(name = "주변 편의시설", description = "장소 주변 편의시설 실시간 검색")
public class NearbyFacilityController {

    private final NearbyFacilityService nearbyFacilityService;

    public NearbyFacilityController(NearbyFacilityService nearbyFacilityService) {
        this.nearbyFacilityService = nearbyFacilityService;
    }

    @GetMapping("/{placeId}/nearby-facilities")
    @Operation(summary = "주변 편의시설 검색")
    public NearbyFacilityResponse search(
            @Parameter(description = "장소 검색 응답의 ID", example = "1") @PathVariable Long placeId,
            @Parameter(description = "현재 지원값: CONVENIENCE_STORE", example = "CONVENIENCE_STORE")
            @RequestParam @NotBlank String types,
            @Parameter(example = "1000") @RequestParam(defaultValue = "1000") @Min(1) int radius
    ) {
        return nearbyFacilityService.search(placeId, types, radius);
    }
}
