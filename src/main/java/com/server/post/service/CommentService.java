package com.server.post.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.domain.Comment;
import com.server.post.domain.Post;
import com.server.post.dto.CommentCreateRequest;
import com.server.post.dto.CommentListResponse;
import com.server.post.dto.CommentResponse;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    /** 오래된 순으로 읽으므로 첫 페이지는 어떤 댓글 ID보다 작은 값에서 시작한다. */
    private static final long FIRST_PAGE_CURSOR = 0L;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CommentResponse create(Long postId, Long userId, CommentCreateRequest request) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User author = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Comment parent = resolveParent(postId, request.parentId());
        Comment comment = commentRepository.save(new Comment(post, author, parent, request.content()));
        postRepository.increaseCommentCount(postId);

        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public CommentListResponse getComments(Long postId, Long cursor, Integer size) {
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        int limit = resolvePageSize(size);
        List<Comment> parents = commentRepository.findTopLevelComments(
                postId,
                cursor == null ? FIRST_PAGE_CURSOR : cursor,
                PageRequest.of(0, limit));

        if (parents.isEmpty()) {
            return new CommentListResponse(List.of(), null);
        }

        List<Long> parentIds = parents.stream().map(Comment::getId).toList();
        Map<Long, List<CommentResponse>> repliesByParent = commentRepository
                .findByParentIdInAndDeletedAtIsNullOrderByIdAsc(parentIds).stream()
                .collect(Collectors.groupingBy(
                        reply -> reply.getParent().getId(),
                        Collectors.mapping(CommentResponse::from, Collectors.toList())));

        List<CommentResponse> items = parents.stream()
                .map(parent -> CommentResponse.from(
                        parent,
                        repliesByParent.getOrDefault(parent.getId(), List.of())))
                .toList();

        Long nextCursor = parents.size() < limit ? null : parents.get(parents.size() - 1).getId();
        return new CommentListResponse(items, nextCursor);
    }

    /**
     * 물리 삭제하지 않는다. 답글이 달린 댓글이어도 답글은 그대로 두고,
     * 목록에서는 작성자와 내용을 감춘 자리만 남는다.
     */
    @Transactional
    public void delete(Long postId, Long commentId, Long userId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getPost().getId().equals(postId)) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (!comment.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.COMMENT_ACCESS_DENIED);
        }

        comment.delete();
        postRepository.decreaseCommentCount(postId);
    }

    /**
     * 답글의 부모를 확인한다. 응답이 최상위 댓글과 답글 두 단계만 표현하므로
     * 답글에 다시 답글을 다는 요청은 거절한다.
     */
    private Comment resolveParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!parent.getPost().getId().equals(postId)) {
            throw new BusinessException(ErrorCode.INVALID_COMMENT_REQUEST);
        }
        if (parent.getParent() != null) {
            throw new BusinessException(ErrorCode.INVALID_COMMENT_REQUEST);
        }
        return parent;
    }

    private int resolvePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
