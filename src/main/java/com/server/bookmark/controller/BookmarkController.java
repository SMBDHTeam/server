package com.server.bookmark.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.bookmark.dto.BookmarkListResponse;
import com.server.bookmark.dto.BookmarkResponse;
import com.server.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@Tag(name = "커뮤니티 북마크", description = "게시물 저장과 내 저장 목록")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping("/posts/{postId}/bookmarks")
    @Operation(
            summary = "게시물 저장",
            description = "이미 저장한 상태에서 다시 요청해도 중복 저장되지 않는다."
    )
    public BookmarkResponse bookmark(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "7") @PathVariable Long postId
    ) {
        Long userId = LoginUser.require(loginUser);
        return bookmarkService.bookmark(postId, userId);
    }

    @DeleteMapping("/posts/{postId}/bookmarks")
    @Operation(
            summary = "게시물 저장 해제",
            description = "저장하지 않은 상태에서 요청해도 오류가 아니다."
    )
    public BookmarkResponse removeBookmark(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "7") @PathVariable Long postId
    ) {
        Long userId = LoginUser.require(loginUser);
        return bookmarkService.removeBookmark(postId, userId);
    }

    @GetMapping("/users/me/bookmarks")
    @Operation(
            summary = "내 북마크 목록",
            description = "최근 저장한 순으로 반환한다. 저장한 뒤 삭제된 게시물은 제외한다."
    )
    public BookmarkListResponse getMyBookmarks(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "한 번에 가져올 게시물 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size
    ) {
        Long userId = LoginUser.require(loginUser);
        return bookmarkService.getMyBookmarks(userId, page, size);
    }
}
