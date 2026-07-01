package com.server.place.controller;

import com.server.place.dto.PlaceDetailResponse;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.service.PlaceService;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public PlaceSearchResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) @Min(1) Integer radius
    ) {
        return placeService.search(keyword, longitude, latitude, radius);
    }

    @GetMapping("/{placeId}")
    public PlaceDetailResponse getDetail(@PathVariable Long placeId) {
        return placeService.getDetail(placeId);
    }
}
