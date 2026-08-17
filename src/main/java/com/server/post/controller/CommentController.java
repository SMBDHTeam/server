package com.server.post.controller;

import com.server.post.dto.CommentCreateRequest;
import com.server.post.dto.CommentListResponse;
import com.server.post.dto.CommentResponse;
import com.server.post.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/v1/posts/{postId}/comments")
@Tag(name = "커뮤니티 댓글", description = "게시물 댓글 작성과 조회")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "댓글 작성",
            description = "parentId 를 주면 답글이 된다. 답글에 다시 답글을 달면 400 을 반환한다."
    )
    public CommentResponse create(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            @Parameter(description = "작성자 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(example = "1") @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return commentService.create(postId, userId, request);
    }

    @GetMapping
    @Operation(
            summary = "댓글 목록",
            description = "최상위 댓글을 오래된 순으로 반환하며 각 댓글의 답글을 함께 담는다. "
                    + "응답의 nextCursor 를 다음 요청의 cursor 로 그대로 넘긴다."
    )
    public CommentListResponse getComments(
            @Parameter(example = "1") @PathVariable Long postId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략한다.", example = "12")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 댓글 수. 1 이상 50 이하", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) Integer size
    ) {
        return commentService.getComments(postId, cursor, size);
    }
}
