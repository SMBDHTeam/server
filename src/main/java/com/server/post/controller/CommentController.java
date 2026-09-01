package com.server.post.controller;

import com.server.auth.service.AuthenticatedUser;
import com.server.auth.web.LoginUser;
import com.server.post.dto.CommentCreateRequest;
import com.server.post.dto.CommentLikeResponse;
import com.server.post.dto.CommentListResponse;
import com.server.post.dto.CommentResponse;
import com.server.post.dto.CommentUpdateRequest;
import com.server.post.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "1") @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        Long userId = LoginUser.require(loginUser);
        return commentService.create(postId, userId, request);
    }

    @PatchMapping("/{commentId}")
    @Operation(
            summary = "댓글 수정",
            description = "내용만 바꾼다. 작성자 본인이 아니면 403 을 반환한다."
    )
    public CommentResponse update(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "7") @PathVariable Long postId,
            @Parameter(example = "3") @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        Long userId = LoginUser.require(loginUser);
        return commentService.update(postId, commentId, userId, request);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "댓글 삭제",
            description = "바로 지우지 않고 삭제 표시만 남긴다. 답글이 달려 있으면 답글은 그대로 보이고 "
                    + "이 댓글은 작성자와 내용이 감춰진 자리로 남는다. 작성자 본인이 아니면 403 을 반환한다."
    )
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "1") @PathVariable Long postId,
            @Parameter(example = "3") @PathVariable Long commentId
    ) {
        Long userId = LoginUser.require(loginUser);
        commentService.delete(postId, commentId, userId);
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
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size,
            @AuthenticationPrincipal AuthenticatedUser loginUser
    ) {
        Long requesterId = LoginUser.idOrNull(loginUser);
        return commentService.getComments(postId, cursor, size, requesterId);
    }

    @PostMapping("/{commentId}/likes")
    @Operation(summary = "댓글 좋아요", description = "이미 누른 상태에서 다시 요청해도 개수가 늘지 않는다.")
    public CommentLikeResponse like(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "1") @PathVariable Long postId,
            @Parameter(example = "3") @PathVariable Long commentId
    ) {
        Long userId = LoginUser.require(loginUser);
        return commentService.like(postId, commentId, userId);
    }

    @DeleteMapping("/{commentId}/likes")
    @Operation(summary = "댓글 좋아요 취소", description = "누른 적 없는 상태에서 요청해도 개수가 줄지 않는다.")
    public CommentLikeResponse unlike(
            @AuthenticationPrincipal AuthenticatedUser loginUser,
            @Parameter(example = "1") @PathVariable Long postId,
            @Parameter(example = "3") @PathVariable Long commentId
    ) {
        Long userId = LoginUser.require(loginUser);
        return commentService.unlike(postId, commentId, userId);
    }
}
