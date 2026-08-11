package com.server.place.controller;

import com.server.place.dto.PlaceDetailResponse;
import com.server.place.dto.PlaceSearchResponse;
import com.server.place.dto.PlaceResolveRequest;
import com.server.place.dto.PlaceResolveResponse;
import com.server.place.service.PlaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/places")
@Tag(name = "장소", description = "내부 DB 장소 검색과 상세 조회")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    @Operation(summary = "장소 검색")
    public PlaceSearchResponse search(
            @Parameter(example = "광안리") @RequestParam(required = false) String keyword,
            @Parameter(example = "129.0403") @RequestParam(required = false) BigDecimal longitude,
            @Parameter(example = "35.1151") @RequestParam(required = false) BigDecimal latitude,
            @Parameter(example = "5000") @RequestParam(required = false) @Min(1) Integer radius,
            @RequestParam(defaultValue = "INTERNAL") String scope,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        if ("INTERNAL".equals(scope) && Integer.valueOf(20).equals(size)) {
            return placeService.search(keyword, longitude, latitude, radius);
        }
        return placeService.search(keyword, longitude, latitude, radius, scope, size);
    }

    @PostMapping("/resolve")
    @Operation(
            summary = "외부 장소 확정",
            description = "사용자가 선택한 외부 검색 결과만 내부 장소로 저장한다. "
                    + "같은 (source, externalId)는 upsert 되며, 좌표 100m 이내이고 공백·기호를 제거한 이름이 "
                    + "완전히 같은 기존 장소가 있으면 그 장소의 placeId를 반환한다. "
                    + "이때 응답 source는 기존 장소의 출처이며 적재해 둔 값은 덮어쓰지 않는다."
    )
    public PlaceResolveResponse resolve(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PlaceResolveRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "naver",
                                            summary = "네이버 지역검색 결과",
                                            value = PlaceOpenApiExamples.RESOLVE_NAVER
                                    ),
                                    @ExampleObject(
                                            name = "kakao",
                                            summary = "카카오 로컬 결과",
                                            value = PlaceOpenApiExamples.RESOLVE_KAKAO
                                    )
                            }
                    )
            )
            @Valid @RequestBody PlaceResolveRequest request) {
        return placeService.resolve(request);
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "장소 상세 조회")
    public PlaceDetailResponse getDetail(
            @Parameter(description = "장소 검색 응답의 ID", example = "1") @PathVariable Long placeId
    ) {
        return placeService.getDetail(placeId);
    }
}
