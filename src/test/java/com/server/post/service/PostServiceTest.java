package com.server.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.hashtag.service.HashtagService;
import com.server.notification.domain.NotificationTargetType;
import com.server.notification.domain.NotificationType;
import com.server.notification.service.NotificationService;
import com.server.place.repository.PlaceRepository;
import com.server.post.domain.Post;
import com.server.post.domain.MediaType;
import com.server.post.dto.PostCreateRequest;
import com.server.post.dto.PostUpdateRequest;
import com.server.post.repository.PostLikeRepository;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostPlaceTagRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("게시물 서비스")
class PostServiceTest {

    private static final long AUTHOR_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long POST_ID = 7L;
    private static final int RESTORE_WINDOW_DAYS = 30;

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
    private final NotificationService notificationService =
            Mockito.mock(NotificationService.class);

    private final PostService postService = new PostService(
            postRepository,
            postMediaRepository,
            postPlaceTagRepository,
            postLikeRepository,
            userRepository,
            placeRepository,
            postSummaryAssembler,
            hashtagService,
            notificationService,
            RESTORE_WINDOW_DAYS);

    @Test
    @DisplayName("이미 누른 좋아요를 다시 눌러도 개수를 올리지 않는다")
    void likeIsIdempotent() {
        when(postRepository.existsByIdAndDeletedAtIsNull(POST_ID)).thenReturn(true);
        givenActiveUser(OTHER_USER_ID);
        // 이미 눌린 좋아요라 새로 들어간 행이 없다.
        when(postLikeRepository.insertIfAbsent(POST_ID, OTHER_USER_ID)).thenReturn(0);
        when(postRepository.findLikeCountById(POST_ID)).thenReturn(1);

        assertThat(postService.like(POST_ID, OTHER_USER_ID).likeCount()).isEqualTo(1);

        verify(postRepository, never()).increaseLikeCount(anyLong());
    }

    @Test
    @DisplayName("좋아요를 처음 눌렀을 때만 작성자에게 알린다")
    void notifiesAuthorOnFirstLikeOnly() {
        when(postRepository.existsByIdAndDeletedAtIsNull(POST_ID)).thenReturn(true);
        givenActiveUser(OTHER_USER_ID);
        when(postRepository.findAuthorIdById(POST_ID)).thenReturn(AUTHOR_ID);
        when(postLikeRepository.insertIfAbsent(POST_ID, OTHER_USER_ID)).thenReturn(1, 0);

        postService.like(POST_ID, OTHER_USER_ID);
        postService.like(POST_ID, OTHER_USER_ID);

        // 두 번 눌러도 실제로 행이 들어간 첫 번째만 알린다.
        verify(notificationService).notify(
                AUTHOR_ID,
                OTHER_USER_ID,
                NotificationType.POST_LIKE,
                NotificationTargetType.POST,
                POST_ID);
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
                postService.update(POST_ID, OTHER_USER_ID, new PostUpdateRequest("고쳐볼까", null, null)))
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

        postService.update(POST_ID, AUTHOR_ID, new PostUpdateRequest("고친 본문 #부산", null, null));

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

    @Test
    @DisplayName("본문만 보내면 사진과 장소 태그는 건드리지 않는다")
    void updateKeepsMediaWhenNotSent() {
        givenPostWrittenBy(AUTHOR_ID);
        when(postMediaRepository.findByPostId(POST_ID)).thenReturn(List.of());
        when(postPlaceTagRepository.findViewsByPostId(POST_ID)).thenReturn(List.of());

        postService.update(POST_ID, AUTHOR_ID, new PostUpdateRequest("본문만 고침", null, null));

        verify(postMediaRepository, never()).deleteByPostId(anyLong());
        verify(postPlaceTagRepository, never()).deleteByPostId(anyLong());
    }

    @Test
    @DisplayName("사진을 보내면 기존 사진을 지우고 보낸 것으로 교체한다")
    void updateReplacesMedia() {
        Post post = givenPostWrittenBy(AUTHOR_ID);
        when(postMediaRepository.findByPostId(POST_ID)).thenReturn(List.of());
        when(postPlaceTagRepository.findViewsByPostId(POST_ID)).thenReturn(List.of());
        List<PostCreateRequest.Media> media = List.of(
                new PostCreateRequest.Media("https://e.com/b.jpg", MediaType.IMAGE, 0));

        postService.update(POST_ID, AUTHOR_ID, new PostUpdateRequest(null, media, null));

        verify(postMediaRepository).deleteByPostId(POST_ID);
        verify(postMediaRepository).saveAll(any());
        // 본문을 안 보냈으므로 해시태그는 다시 계산하지 않는다.
        verify(hashtagService, never()).reattachFromContent(eq(post), any());
    }

    @Test
    @DisplayName("장소 태그를 빈 배열로 보내면 전부 없앤다")
    void updateClearsPlaceTags() {
        givenPostWrittenBy(AUTHOR_ID);
        when(postMediaRepository.findByPostId(POST_ID)).thenReturn(List.of());
        when(postPlaceTagRepository.findViewsByPostId(POST_ID)).thenReturn(List.of());

        postService.update(POST_ID, AUTHOR_ID, new PostUpdateRequest(null, null, List.of()));

        verify(postPlaceTagRepository).deleteByPostId(POST_ID);
        verify(postPlaceTagRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("바꿀 항목을 하나도 보내지 않으면 거절한다")
    void updateRejectsEmptyRequest() {
        assertThatThrownBy(() ->
                postService.update(POST_ID, AUTHOR_ID, new PostUpdateRequest(null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_POST_REQUEST));
    }

    @Test
    @DisplayName("복구하면 삭제 표시가 지워지고 해시태그를 다시 연결한다")
    void restoreClearsDeletionAndReattachesHashtags() {
        Post post = givenDeletedPost(AUTHOR_ID, LocalDateTime.now().minusDays(3));
        when(postMediaRepository.findByPostId(POST_ID)).thenReturn(List.of());
        when(postPlaceTagRepository.findViewsByPostId(POST_ID)).thenReturn(List.of());

        postService.restore(POST_ID, AUTHOR_ID);

        assertThat(post.getDeletedAt()).isNull();
        // 삭제할 때 연결을 지웠으므로 본문에서 다시 뽑지 않으면 태그 필터 피드에서 빠진다.
        verify(hashtagService).attachFromContent(post, "본문");
    }

    @Test
    @DisplayName("삭제한 지 30일이 지나면 복구할 수 없다")
    void restoreRejectsExpiredPost() {
        Post post = givenDeletedPost(AUTHOR_ID, LocalDateTime.now().minusDays(31));

        assertThatThrownBy(() -> postService.restore(POST_ID, AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.POST_RESTORE_WINDOW_EXPIRED));
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("남의 게시물은 복구할 수 없다")
    void restoreRejectsOtherUsersPost() {
        Post post = givenDeletedPost(AUTHOR_ID, LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> postService.restore(POST_ID, OTHER_USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제하지 않은 게시물에는 복구가 걸리지 않는다")
    void restoreRejectsLivePost() {
        when(postRepository.findDeletedById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.restore(POST_ID, AUTHOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("인기 피드는 한 번에 50건까지만 준다")
    void popularFeedCapsPageSize() {
        when(postRepository.findPopularFeed(any(), any(), any())).thenReturn(List.of());
        when(postSummaryAssembler.assemble(List.of(), null)).thenReturn(List.of());

        postService.getPopularFeed(0, 500, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findPopularFeed(any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("인기 피드는 점수 순이라 이어받을 커서를 주지 않는다")
    void popularFeedHasNoCursor() {
        when(postRepository.findPopularFeed(any(), any(), any())).thenReturn(List.of());
        when(postSummaryAssembler.assemble(List.of(), null)).thenReturn(List.of());

        assertThat(postService.getPopularFeed(0, 20, null).nextCursor()).isNull();
    }

    private Post givenDeletedPost(long userId, LocalDateTime deletedAt) {
        Post post = new Post(givenActiveUser(userId), "본문");
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "deletedAt", deletedAt);
        when(postRepository.findDeletedById(POST_ID)).thenReturn(Optional.of(post));
        return post;
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
        when(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true);
        return user;
    }
}
