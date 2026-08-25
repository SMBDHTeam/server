package com.server.post.service;

import com.server.block.repository.BlockRepository;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.notification.service.NotificationService;
import com.server.post.domain.Comment;
import com.server.post.domain.Post;
import com.server.post.dto.CommentCreateRequest;
import com.server.post.dto.CommentLikeResponse;
import com.server.post.dto.CommentListResponse;
import com.server.post.dto.CommentResponse;
import com.server.post.dto.CommentUpdateRequest;
import com.server.post.repository.CommentLikeRepository;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public CommentService(
            CommentRepository commentRepository,
            CommentLikeRepository commentLikeRepository,
            PostRepository postRepository,
            BlockRepository blockRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.postRepository = postRepository;
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * 차단 관계가 있으면 어느 쪽이 차단했든 댓글을 쓸 수 없다. 차단은 상대가 내 계정에
     * 접근하지 못하게 하는 것이므로, 차단한 사람이 내 글에 계속 댓글을 다는 상황을 막는다.
     * 오류 문구에 차단을 드러내지 않아 누가 차단했는지는 알 수 없다.
     */
    @Transactional
    public CommentResponse create(Long postId, Long userId, CommentCreateRequest request) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User author = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 본인 글이면 차단 관계가 있을 수 없어 조회를 건너뛴다.
        Long postAuthorId = post.getUser().getId();
        if (!postAuthorId.equals(userId) && blockRepository.existsBetween(postAuthorId, userId)) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_ALLOWED);
        }

        Comment parent = resolveParent(postId, request.parentId());
        Comment comment = commentRepository.save(new Comment(post, author, parent, request.content()));
        postRepository.increaseCommentCount(postId);

        // 답글이면 부모 댓글 작성자에게, 일반 댓글이면 게시물 작성자에게 알린다.
        if (parent == null) {
            notificationService.notify(
                    postAuthorId,
                    userId,
                    NotificationType.COMMENT,
                    NotificationTargetType.COMMENT,
                    comment.getId());
        } else {
            notificationService.notify(
                    parent.getUser().getId(),
                    userId,
                    NotificationType.COMMENT_REPLY,
                    NotificationTargetType.COMMENT,
                    comment.getId());
        }
        return CommentResponse.from(comment, false);
    }

    @Transactional(readOnly = true)
    public CommentListResponse getComments(
            Long postId, Long cursor, Integer size, Long requesterId) {
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
        List<Comment> replies = commentRepository.findRepliesByParentIds(parentIds);

        // 부모와 답글의 좋아요 여부를 한 번에 읽는다.
        List<Long> allIds = new ArrayList<>(parentIds);
        replies.forEach(reply -> allIds.add(reply.getId()));
        Set<Long> likedIds = likedCommentIds(requesterId, allIds);

        Map<Long, List<CommentResponse>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(
                        reply -> reply.getParent().getId(),
                        Collectors.mapping(
                                reply -> CommentResponse.from(reply, likedIds.contains(reply.getId())),
                                Collectors.toList())));

        List<CommentResponse> items = parents.stream()
                .map(parent -> CommentResponse.from(
                        parent,
                        likedIds.contains(parent.getId()),
                        repliesByParent.getOrDefault(parent.getId(), List.of())))
                .toList();

        Long nextCursor = parents.size() < limit ? null : parents.get(parents.size() - 1).getId();
        return new CommentListResponse(items, nextCursor);
    }

    /** 이미 눌린 좋아요를 다시 눌러도 개수가 늘지 않는다. */
    @Transactional
    public CommentLikeResponse like(Long postId, Long commentId, Long userId) {
        findComment(postId, commentId);
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 처음 누른 경우에만 알린다. 취소 후 다시 눌러도 알림이 또 가지 않도록 한다.
        if (commentLikeRepository.insertIfAbsent(commentId, userId) > 0) {
            commentRepository.increaseLikeCount(commentId);
            notificationService.notify(
                    commentRepository.findAuthorIdById(commentId),
                    userId,
                    NotificationType.COMMENT_LIKE,
                    NotificationTargetType.COMMENT,
                    commentId);
        }
        return new CommentLikeResponse(commentRepository.findLikeCountById(commentId), true);
    }

    /** 누른 적 없는 좋아요를 취소해도 개수가 줄지 않는다. */
    @Transactional
    public CommentLikeResponse unlike(Long postId, Long commentId, Long userId) {
        findComment(postId, commentId);
        if (commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId) > 0) {
            commentRepository.decreaseLikeCount(commentId);
        }
        return new CommentLikeResponse(commentRepository.findLikeCountById(commentId), false);
    }

    private Comment findComment(Long postId, Long commentId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getPost().getId().equals(postId)) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private Set<Long> likedCommentIds(Long requesterId, List<Long> commentIds) {
        if (requesterId == null || commentIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(commentLikeRepository.findLikedCommentIds(requesterId, commentIds));
    }

    /** 내용만 바꾼다. 답글 관계와 좋아요 수는 그대로 둔다. */
    @Transactional
    public CommentResponse update(
            Long postId, Long commentId, Long userId, CommentUpdateRequest request) {
        Comment comment = findWritableComment(postId, commentId, userId);
        comment.updateContent(request.content());

        // 좋아요 여부는 본인이 누른 것만 보면 된다.
        return CommentResponse.from(
                comment, commentLikeRepository.existsByCommentIdAndUserId(commentId, userId));
    }

    /**
     * 물리 삭제하지 않는다. 답글이 달린 댓글이어도 답글은 그대로 두고,
     * 목록에서는 작성자와 내용을 감춘 자리만 남는다.
     */
    @Transactional
    public void delete(Long postId, Long commentId, Long userId) {
        findWritableComment(postId, commentId, userId).delete();
        postRepository.decreaseCommentCount(postId);
    }

    private Comment findWritableComment(Long postId, Long commentId, Long userId) {
        Comment comment = findComment(postId, commentId);
        if (!comment.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.COMMENT_ACCESS_DENIED);
        }
        return comment;
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
