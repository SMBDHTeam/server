package com.server.hashtag.controller;

import com.server.hashtag.dto.HashtagPlaceListResponse;
import com.server.hashtag.dto.HashtagSuggestionListResponse;
import com.server.hashtag.service.HashtagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "커뮤니티 카테고리", description = "카테고리 목록과 자동완성")
public class CategoryController {

    private final HashtagService hashtagService;

    public CategoryController(HashtagService hashtagService) {
        this.hashtagService = hashtagService;
    }

    @GetMapping
    @Operation(
            summary = "카테고리 목록",
            description = "쓸 수 있는 카테고리 전체를 등록 순서로 반환한다. 사용자가 새로 만들 수 "
                    + "없으므로 이 목록이 곧 선택지이며, 화면의 탭에 그대로 쓴다.\n\n"
                    + "탭을 누르면 GET /api/v1/posts?category={name} 으로 거른다. "
                    + "화면의 \"전체\" 탭은 category 없이 조회한다."
    )
    public HashtagSuggestionListResponse findAll() {
        return hashtagService.findAll();
    }

    @GetMapping("/search")
    @Operation(
            summary = "카테고리 자동완성",
            description = "입력한 앞글자로 시작하는 카테고리를 많이 쓰인 순으로 반환한다. "
                    + "본문에 여러 단어를 담으려면 붙여 써야 하므로, 사용자가 직접 입력하는 대신 "
                    + "여기서 골라 넣도록 하는 것이 낫다."
    )
    public HashtagSuggestionListResponse suggest(
            @Parameter(description = "카테고리 앞글자", example = "광")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "최대 후보 수. 1 이상 30 이하", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) Integer size
    ) {
        return hashtagService.suggest(keyword, size);
    }

    @GetMapping("/{name}/places")
    @Operation(
            summary = "카테고리가 가리키는 장소",
            description = "이 카테고리가 붙은 게시물들이 태그한 장소를, 언급한 사람이 많은 순으로 반환한다. "
                    + "카테고리를 눌렀을 때 글 목록 대신 지도를 보여주는 데 쓴다.\n\n"
                    + "정확도를 위해 두 가지 조건을 둔다. 장소를 하나만 태그한 게시물만 세고, "
                    + "서로 다른 사람 3명 이상이 언급한 장소만 반환한다. 한 글에 여러 장소를 "
                    + "태그하면 그 태그가 어디를 가리키는지 알 수 없고, 한 사람의 태그만으로 "
                    + "순위가 만들어지면 목록을 믿을 수 없기 때문이다.\n\n"
                    + "사용자들이 붙인 것을 모은 결과이므로 확정된 분류가 아니다."
    )
    public HashtagPlaceListResponse findPlaces(
            @Parameter(description = "카테고리 이름", example = "맛집")
            @PathVariable String name,
            @Parameter(description = "최대 장소 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        return hashtagService.findPlaces(name, size);
    }
}
