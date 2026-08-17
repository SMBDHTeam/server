package com.server.post.controller;

import com.server.post.dto.PostCreateRequest;
import com.server.post.dto.PostDetailResponse;
import com.server.post.dto.PostLikeResponse;
import com.server.post.dto.PostSummaryListResponse;
import com.server.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "커뮤니티 게시물", description = "여행 후기 게시물 작성과 조회")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "게시물 작성",
            description = "미디어는 이미 업로드된 URL 을 전달한다. 장소 태그는 내부 places 에 등록된 장소만 지정할 수 있다."
    )
    public PostDetailResponse create(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "작성자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PostCreateRequest request
    ) {
        return postService.create(userId, request);
    }

    @GetMapping
    @Operation(
            summary = "피드 조회",
            description = "최신순으로 한 페이지를 반환한다. 응답의 nextCursor 를 다음 요청의 cursor 로 그대로 넘긴다. "
                    + "nextCursor 가 null 이면 더 가져올 게시물이 없다."
    )
    public PostSummaryListResponse getFeed(
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략한다.", example = "81")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 게시물 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) Integer size
    ) {
        return postService.getFeed(cursor, size);
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시물 상세", description = "첨부 미디어와 장소 태그를 모두 포함한다.")
    public PostDetailResponse get(
            @Parameter(example = "7") @PathVariable Long postId
    ) {
        return postService.get(postId);
    }

    @PostMapping("/{postId}/likes")
    @Operation(
            summary = "좋아요",
            description = "이미 누른 상태에서 다시 요청해도 개수가 늘지 않는다."
    )
    public PostLikeResponse like(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "요청자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(example = "7") @PathVariable Long postId
    ) {
        return postService.like(postId, userId);
    }

    @DeleteMapping("/{postId}/likes")
    @Operation(
            summary = "좋아요 취소",
            description = "누른 적 없는 상태에서 요청해도 개수가 줄지 않는다."
    )
    public PostLikeResponse unlike(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "요청자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(example = "7") @PathVariable Long postId
    ) {
        return postService.unlike(postId, userId);
    }
}
