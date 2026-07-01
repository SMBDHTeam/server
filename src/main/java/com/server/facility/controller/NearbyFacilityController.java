package com.server.facility.controller;

import com.server.facility.dto.NearbyFacilityResponse;
import com.server.facility.service.NearbyFacilityService;
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
public class NearbyFacilityController {

    private final NearbyFacilityService nearbyFacilityService;

    public NearbyFacilityController(NearbyFacilityService nearbyFacilityService) {
        this.nearbyFacilityService = nearbyFacilityService;
    }

    @GetMapping("/{placeId}/nearby-facilities")
    public NearbyFacilityResponse search(
            @PathVariable Long placeId,
            @RequestParam @NotBlank String types,
            @RequestParam(defaultValue = "1000") @Min(1) int radius
    ) {
        return nearbyFacilityService.search(placeId, types, radius);
    }
}
