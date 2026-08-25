package com.server.hashtag.controller;

import com.server.hashtag.dto.HashtagSuggestionListResponse;
import com.server.hashtag.service.HashtagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/hashtags")
@Tag(name = "커뮤니티 해시태그", description = "해시태그 자동완성")
public class HashtagController {

    private final HashtagService hashtagService;

    public HashtagController(HashtagService hashtagService) {
        this.hashtagService = hashtagService;
    }

    @GetMapping("/search")
    @Operation(
            summary = "해시태그 자동완성",
            description = "입력한 앞글자로 시작하는 태그를 많이 쓰인 순으로 반환한다. "
                    + "본문에 여러 단어를 담으려면 붙여 써야 하므로, 사용자가 직접 입력하는 대신 "
                    + "여기서 골라 넣도록 하는 것이 낫다."
    )
    public HashtagSuggestionListResponse suggest(
            @Parameter(description = "태그 앞글자. # 은 빼고 보낸다.", example = "광")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "최대 후보 수. 1 이상 30 이하", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) Integer size
    ) {
        return hashtagService.suggest(keyword, size);
    }
}
