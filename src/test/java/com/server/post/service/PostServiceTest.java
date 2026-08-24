package com.server.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.hashtag.service.HashtagService;
import com.server.place.repository.PlaceRepository;
import com.server.post.domain.Post;
import com.server.post.domain.PostLike;
import com.server.post.dto.PostUpdateRequest;
import com.server.post.repository.PostLikeRepository;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostPlaceTagRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("게시물 서비스")
class PostServiceTest {

    private static final long AUTHOR_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long POST_ID = 7L;

    private final PostRepository postRepository = Mockito.mock(PostRepository.class);
    private final PostMediaRepository postMediaRepository = Mockito.mock(PostMediaRepository.class);
    private final PostPlaceTagRepository postPlaceTagRepository =
            Mockito.mock(PostPlaceTagRepository.class);
    private final PostLikeRepository postLikeRepository = Mockito.mock(PostLikeRepository.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
    private final PostSummaryAssembler postSummaryAssembler =
            Mockito.mock(PostSummaryAssembler.class);
    private final HashtagService hashtagService = Mockito.mock(HashtagService.class);

    private final PostService postService = new PostService(
            postRepository,
            postMediaRepository,
            postPlaceTagRepository,
            postLikeRepository,
            userRepository,
            placeRepository,
            postSummaryAssembler,
            hashtagService);

    @Test
    @DisplayName("이미 누른 좋아요를 다시 눌러도 개수를 올리지 않는다")
    void likeIsIdempotent() {
        givenPostWrittenBy(AUTHOR_ID);
        givenActiveUser(OTHER_USER_ID);
        when(postLikeRepository.existsByPostIdAndUserId(POST_ID, OTHER_USER_ID)).thenReturn(true);
        when(postRepository.findLikeCountById(POST_ID)).thenReturn(1);

        assertThat(postService.like(POST_ID, OTHER_USER_ID).likeCount()).isEqualTo(1);

        verify(postLikeRepository, never()).save(any(PostLike.class));
        verify(postRepository, never()).increaseLikeCount(anyLong());
    }

    @Test
    @DisplayName("누른 적 없는 좋아요를 취소해도 개수를 내리지 않는다")
    void unlikeIsIdempotent() {
        when(postRepository.existsByIdAndDeletedAtIsNull(POST_ID)).thenReturn(true);
        when(postLikeRepository.deleteByPostIdAndUserId(POST_ID, OTHER_USER_ID)).thenReturn(0L);
        when(postRepository.findLikeCountById(POST_ID)).thenReturn(0);

        assertThat(postService.unlike(POST_ID, OTHER_USER_ID).liked()).isFalse();

        verify(postRepository, never()).decreaseLikeCount(anyLong());
    }

    @Test
    @DisplayName("남의 게시물은 수정할 수 없다")
    void updateRejectsOtherUsersPost() {
        givenPostWrittenBy(AUTHOR_ID);

        assertThatThrownBy(() ->
                postService.update(POST_ID, OTHER_USER_ID, new PostUpdateRequest("고쳐볼까")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("남의 게시물은 삭제할 수 없다")
    void deleteRejectsOtherUsersPost() {
        Post post = givenPostWrittenBy(AUTHOR_ID);

        assertThatThrownBy(() -> postService.delete(POST_ID, OTHER_USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
        assertThat(post.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("삭제는 행을 지우지 않고 삭제 시각만 남긴다")
    void deleteKeepsRowForRecovery() {
        Post post = givenPostWrittenBy(AUTHOR_ID);

        postService.delete(POST_ID, AUTHOR_ID);

        assertThat(post.getDeletedAt()).isNotNull();
        verify(postRepository, never()).delete(any(Post.class));
        verify(hashtagService).detachFromPost(POST_ID);
    }

    @Test
    @DisplayName("본문을 수정하면 해시태그를 다시 계산한다")
    void updateRecalculatesHashtags() {
        Post post = givenPostWrittenBy(AUTHOR_ID);
        when(postMediaRepository.findByPostId(POST_ID)).thenReturn(List.of());
        when(postPlaceTagRepository.findViewsByPostId(POST_ID)).thenReturn(List.of());

        postService.update(POST_ID, AUTHOR_ID, new PostUpdateRequest("고친 본문 #부산"));

        assertThat(post.getContent()).isEqualTo("고친 본문 #부산");
        verify(hashtagService).reattachFromContent(post, "고친 본문 #부산");
    }

    @Test
    @DisplayName("팔로잉 피드는 요청자를 알 수 없으면 거절한다")
    void followingFeedRequiresRequester() {
        assertThatThrownBy(() -> postService.getFeed(null, 20, true, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_FEED_REQUEST);
                    assertThat(exception.getFieldViolations())
                            .singleElement()
                            .satisfies(violation ->
                                    assertThat(violation.field()).isEqualTo("X-User-Id"));
                });
    }

    @Test
    @DisplayName("요청자를 몰라도 전체 피드는 볼 수 있다")
    void anonymousCanReadFeed() {
        when(postRepository.findFeed(any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(List.of());
        when(postSummaryAssembler.assemble(List.of(), null)).thenReturn(List.of());

        assertThatCode(() -> postService.getFeed(null, 20, false, null, null, null))
                .doesNotThrowAnyException();
    }

    private Post givenPostWrittenBy(long userId) {
        Post post = new Post(givenActiveUser(userId), "본문");
        ReflectionTestUtils.setField(post, "id", POST_ID);
        when(postRepository.findByIdAndDeletedAtIsNull(POST_ID)).thenReturn(Optional.of(post));
        return post;
    }

    private User givenActiveUser(long userId) {
        User user = new User("사용자" + userId, null);
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        return user;
    }
}
