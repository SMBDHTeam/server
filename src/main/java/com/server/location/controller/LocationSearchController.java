package com.server.location.controller;

import com.server.location.dto.LocationSearchResponse;
import com.server.location.service.LocationSearchService;
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
public class LocationSearchController {

    private final LocationSearchService locationSearchService;

    public LocationSearchController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    @GetMapping("/search")
    public LocationSearchResponse search(
            @RequestParam @NotBlank String keyword,
            @RequestParam(defaultValue = "10") @Min(1) int size
    ) {
        return locationSearchService.search(keyword, size);
    }
}
