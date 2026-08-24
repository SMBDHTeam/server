package com.server.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.domain.Comment;
import com.server.post.domain.Post;
import com.server.post.dto.CommentCreateRequest;
import com.server.post.dto.CommentResponse;
import com.server.post.repository.CommentLikeRepository;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("댓글 서비스")
class CommentServiceTest {

    private static final long POST_ID = 7L;
    private static final long OTHER_POST_ID = 8L;
    private static final long AUTHOR_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    private final CommentRepository commentRepository = Mockito.mock(CommentRepository.class);
    private final CommentLikeRepository commentLikeRepository =
            Mockito.mock(CommentLikeRepository.class);
    private final PostRepository postRepository = Mockito.mock(PostRepository.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);

    private final CommentService commentService = new CommentService(
            commentRepository, commentLikeRepository, postRepository, userRepository);

    @Test
    @DisplayName("답글에 다시 답글을 달 수 없다")
    void rejectsReplyToReply() {
        Post post = givenPost(POST_ID);
        givenActiveUser(AUTHOR_ID);
        Comment parent = comment(10L, post, null);
        Comment reply = comment(11L, post, parent);
        when(commentRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.create(
                POST_ID, AUTHOR_ID, new CommentCreateRequest("답글의 답글", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_COMMENT_REQUEST));
    }

    @Test
    @DisplayName("다른 게시물의 댓글에는 답글을 달 수 없다")
    void rejectsParentFromAnotherPost() {
        givenPost(POST_ID);
        givenActiveUser(AUTHOR_ID);
        Comment parent = comment(10L, givenPost(OTHER_POST_ID), null);
        when(commentRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.create(
                POST_ID, AUTHOR_ID, new CommentCreateRequest("남의 글 댓글에 답글", 10L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_COMMENT_REQUEST));
    }

    @Test
    @DisplayName("남의 댓글은 삭제할 수 없다")
    void deleteRejectsOtherUsersComment() {
        Comment comment = comment(10L, givenPost(POST_ID), null);
        when(commentRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(POST_ID, 10L, OTHER_USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.COMMENT_ACCESS_DENIED));
        assertThat(comment.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("경로의 게시물과 댓글 소속이 다르면 찾지 못한 것으로 본다")
    void deleteRejectsCommentFromAnotherPost() {
        Comment comment = comment(10L, givenPost(OTHER_POST_ID), null);
        when(commentRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(POST_ID, 10L, AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제는 행을 지우지 않고 삭제 시각만 남기며 댓글 수를 줄인다")
    void deleteKeepsRow() {
        Comment comment = comment(10L, givenPost(POST_ID), null);
        when(commentRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(comment));

        commentService.delete(POST_ID, 10L, AUTHOR_ID);

        assertThat(comment.getDeletedAt()).isNotNull();
        verify(commentRepository, never()).delete(any(Comment.class));
        verify(postRepository).decreaseCommentCount(POST_ID);
    }

    @Test
    @DisplayName("삭제된 댓글도 답글이 남아 있으면 자리를 유지하고 작성자와 내용을 감춘다")
    void keepsDeletedParentAsPlaceholder() {
        Post post = givenPost(POST_ID);
        Comment parent = comment(10L, post, null);
        parent.delete();
        Comment reply = comment(11L, post, parent);

        when(postRepository.existsByIdAndDeletedAtIsNull(POST_ID)).thenReturn(true);
        when(commentRepository.findTopLevelComments(anyLong(), anyLong(), any()))
                .thenReturn(List.of(parent));
        when(commentRepository.findByParentIdInAndDeletedAtIsNullOrderByIdAsc(List.of(10L)))
                .thenReturn(List.of(reply));

        CommentResponse item = commentService.getComments(POST_ID, null, 20, null).items().get(0);

        assertThat(item.deleted()).isTrue();
        assertThat(item.author()).isNull();
        assertThat(item.content()).isNull();
        assertThat(item.replies()).singleElement()
                .satisfies(child -> assertThat(child.content()).isEqualTo("내용"));
    }

    @Test
    @DisplayName("이미 누른 댓글 좋아요를 다시 눌러도 개수를 올리지 않는다")
    void commentLikeIsIdempotent() {
        Comment comment = comment(10L, givenPost(POST_ID), null);
        when(commentRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(comment));
        givenActiveUser(OTHER_USER_ID);
        when(commentLikeRepository.existsByCommentIdAndUserId(10L, OTHER_USER_ID)).thenReturn(true);
        when(commentRepository.findLikeCountById(10L)).thenReturn(1);

        assertThat(commentService.like(POST_ID, 10L, OTHER_USER_ID).likeCount()).isEqualTo(1);

        verify(commentRepository, never()).increaseLikeCount(anyLong());
    }

    private Post givenPost(long postId) {
        Post post = new Post(user(AUTHOR_ID), "본문");
        ReflectionTestUtils.setField(post, "id", postId);
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        return post;
    }

    private Comment comment(long commentId, Post post, Comment parent) {
        Comment comment = new Comment(post, user(AUTHOR_ID), parent, "내용");
        ReflectionTestUtils.setField(comment, "id", commentId);
        return comment;
    }

    private User givenActiveUser(long userId) {
        User user = user(userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        return user;
    }

    private User user(long userId) {
        User user = new User("사용자" + userId, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
