package com.server.spontaneous;

import com.server.location.dto.LocationSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spontaneous-trips/start-locations")
@Tag(name = "즉흥여행", description = "부산광역시 전용 즉흥여행")
public class SpontaneousStartLocationSearchController {

    private final SpontaneousStartLocationSearchService searchService;

    public SpontaneousStartLocationSearchController(SpontaneousStartLocationSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    @Operation(summary = "즉흥여행 부산 출발지 검색")
    public LocationSearchResponse search(
            @Parameter(example = "부산역") @RequestParam @NotBlank String keyword,
            @Parameter(description = "검색 결과 수. 부산 필터 적용 후에는 더 적을 수 있다.", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size
    ) {
        return searchService.search(keyword, size);
    }
}
