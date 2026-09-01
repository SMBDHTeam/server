package com.server.user.controller;

import com.server.user.dto.UserSearchListResponse;
import com.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로 변수를 쓰는 {@link UserController}와 섞이지 않도록 {@code /users/search} 를 별도로 매핑한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/users/search")
@Tag(name = "커뮤니티 프로필", description = "사용자 프로필과 작성 게시물")
public class UserSearchController {

    private final UserService userService;

    public UserSearchController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(
            summary = "사용자 검색",
            description = "닉네임에 검색어가 포함된 사용자를 가나다순으로 반환한다. "
                    + "검색어가 비어 있으면 빈 목록을 준다."
    )
    public UserSearchListResponse search(
            @Parameter(description = "닉네임 검색어", example = "감자")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "한 번에 가져올 인원 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        return userService.search(keyword, page, size);
    }
}
