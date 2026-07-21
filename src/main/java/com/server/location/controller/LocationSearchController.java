package com.server.location.controller;

import com.server.location.dto.LocationSearchResponse;
import com.server.location.service.LocationSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "출발지·도착지", description = "Kakao Local 기반 위치 검색")
public class LocationSearchController {

    private final LocationSearchService locationSearchService;

    public LocationSearchController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    @GetMapping("/search")
    @Operation(summary = "출발지·도착지 검색")
    public LocationSearchResponse search(
            @Parameter(example = "부산역") @RequestParam @NotBlank String keyword,
            @Parameter(example = "5") @RequestParam(defaultValue = "10") @Min(1) int size
    ) {
        return locationSearchService.search(keyword, size);
    }
}
