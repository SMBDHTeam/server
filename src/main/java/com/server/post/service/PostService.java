package com.server.post.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.FieldViolation;
import com.server.hashtag.service.HashtagService;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import com.server.post.domain.PostPlaceTag;
import com.server.post.dto.PostCreateRequest;
import com.server.post.dto.PostDetailResponse;
import com.server.post.dto.PostLikeResponse;
import com.server.post.dto.PostPlaceTagView;
import com.server.post.dto.PostSummaryListResponse;
import com.server.post.dto.PostUpdateRequest;
import com.server.post.repository.PostLikeRepository;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostPlaceTagRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private static final int DEFAULT_FEED_SIZE = 20;
    private static final int MAX_FEED_SIZE = 50;
    /** 첫 페이지는 커서가 없으므로 어떤 게시물 ID보다 큰 값에서 시작한다. */
    private static final long FIRST_PAGE_CURSOR = Long.MAX_VALUE;
    /** 인기 피드가 다루는 기간. 지나치게 넓으면 예전 인기글이 상단을 계속 차지한다. */
    private static final int POPULAR_FEED_DAYS = 7;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostPlaceTagRepository postPlaceTagRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final PostSummaryAssembler postSummaryAssembler;
    private final HashtagService hashtagService;

    public PostService(
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            PostPlaceTagRepository postPlaceTagRepository,
            PostLikeRepository postLikeRepository,
            UserRepository userRepository,
            PlaceRepository placeRepository,
            PostSummaryAssembler postSummaryAssembler,
            HashtagService hashtagService
    ) {
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.postPlaceTagRepository = postPlaceTagRepository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.placeRepository = placeRepository;
        this.postSummaryAssembler = postSummaryAssembler;
        this.hashtagService = hashtagService;
    }

    @Transactional
    public PostDetailResponse create(Long userId, PostCreateRequest request) {
        User author = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.save(new Post(author, request.content()));
        List<PostMedia> mediaList = saveMedia(post, request.mediaList());
        List<PostPlaceTag> placeTags = savePlaceTags(post, request.placeTags());
        List<String> hashtags = hashtagService.attachFromContent(post, request.content());

        // 방금 만든 게시물이라 좋아요·저장이 있을 수 없다.
        return PostDetailResponse.from(
                post,
                mediaList,
                placeTags.stream().map(PostPlaceTagView::from).toList(),
                hashtags,
                false,
                false);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse get(Long postId, Long requesterId) {
        Post post = postRepository.findReadableById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        List<Long> postIds = List.of(postId);
        return PostDetailResponse.from(
                post,
                postMediaRepository.findByPostId(postId),
                postPlaceTagRepository.findViewsByPostId(postId),
                hashtagService.findNamesByPostIds(postIds).getOrDefault(postId, List.of()),
                !postSummaryAssembler.likedPostIds(requesterId, postIds).isEmpty(),
                !postSummaryAssembler.bookmarkedPostIds(requesterId, postIds).isEmpty());
    }

    /**
     * @param following   true 면 요청자가 팔로우한 사람들의 게시물만 반환한다.
     * @param placeId     값이 있으면 이 장소를 태그한 게시물만 반환한다.
     * @param hashtag     값이 있으면 이 해시태그가 달린 게시물만 반환한다.
     * @param requesterId 팔로잉 피드에 필요하며, 차단한 사용자를 걸러내는 데도 쓴다.
     */
    @Transactional(readOnly = true)
    public PostSummaryListResponse getFeed(
            Long cursor,
            Integer size,
            boolean following,
            Long placeId,
            String hashtag,
            Long requesterId
    ) {
        if (following && requesterId == null) {
            throw new BusinessException(ErrorCode.INVALID_FEED_REQUEST, List.of(new FieldViolation(
                    "X-User-Id", "팔로잉 피드를 보려면 요청자를 알 수 있어야 합니다.")));
        }
        int limit = resolveFeedSize(size);
        return toSummaryList(
                postRepository.findFeed(
                        following ? requesterId : null,
                        placeId,
                        hashtag == null || hashtag.isBlank() ? null : hashtag.trim().toLowerCase(),
                        requesterId,
                        cursor == null ? FIRST_PAGE_CURSOR : cursor,
                        PageRequest.of(0, limit)),
                limit,
                requesterId);
    }

    /** 탐색 탭. 최근 {@value #POPULAR_FEED_DAYS}일 게시물을 좋아요·댓글 기준으로 정렬한다. */
    @Transactional(readOnly = true)
    public PostSummaryListResponse getPopularFeed(Integer page, Integer size, Long requesterId) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int limit = resolveFeedSize(size);
        List<Post> posts = postRepository.findPopularFeed(
                LocalDateTime.now().minusDays(POPULAR_FEED_DAYS),
                requesterId,
                PageRequest.of(resolvedPage, limit));

        // 점수 기준 정렬이라 이어받을 커서가 없다. 다음 페이지는 page 를 올려 요청한다.
        return new PostSummaryListResponse(
                postSummaryAssembler.assemble(posts, requesterId), null);
    }

    @Transactional(readOnly = true)
    public PostSummaryListResponse getUserPosts(
            Long userId, Long cursor, Integer size, Long requesterId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int limit = resolveFeedSize(size);
        return toSummaryList(
                postRepository.findByUserIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        userId,
                        cursor == null ? FIRST_PAGE_CURSOR : cursor,
                        PageRequest.of(0, limit)),
                limit,
                requesterId);
    }

    private PostSummaryListResponse toSummaryList(List<Post> posts, int limit, Long requesterId) {
        // 요청한 개수를 못 채웠으면 더 가져올 게시물이 없다.
        Long nextCursor = posts.size() < limit ? null : posts.get(posts.size() - 1).getId();
        return new PostSummaryListResponse(
                postSummaryAssembler.assemble(posts, requesterId), nextCursor);
    }

    @Transactional
    public PostDetailResponse update(Long postId, Long userId, PostUpdateRequest request) {
        Post post = findWritablePost(postId, userId);
        post.updateContent(request.content());
        List<String> hashtags = hashtagService.reattachFromContent(post, request.content());

        List<Long> postIds = List.of(postId);
        return PostDetailResponse.from(
                post,
                postMediaRepository.findByPostId(postId),
                postPlaceTagRepository.findViewsByPostId(postId),
                hashtags,
                !postSummaryAssembler.likedPostIds(userId, postIds).isEmpty(),
                !postSummaryAssembler.bookmarkedPostIds(userId, postIds).isEmpty());
    }

    /**
     * 물리 삭제하지 않고 {@code deletedAt}만 남긴다. 조회 경로가 모두
     * {@code deletedAt IS NULL} 조건을 쓰므로 삭제된 게시물은 응답에 나오지 않는다.
     */
    @Transactional
    public void delete(Long postId, Long userId) {
        findWritablePost(postId, userId).delete();
        hashtagService.detachFromPost(postId);
    }

    private Post findWritablePost(Long postId, Long userId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
        return post;
    }

    /**
     * 이미 눌린 좋아요를 다시 눌러도 개수가 늘지 않는다. 같은 요청이 동시에 들어와도
     * 실제로 행이 들어간 쪽만 개수를 올린다.
     */
    @Transactional
    public PostLikeResponse like(Long postId, Long userId) {
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (postLikeRepository.insertIfAbsent(postId, userId) > 0) {
            postRepository.increaseLikeCount(postId);
        }
        return new PostLikeResponse(postRepository.findLikeCountById(postId), true);
    }

    /** 누른 적 없는 좋아요를 취소해도 개수가 줄지 않는다. */
    @Transactional
    public PostLikeResponse unlike(Long postId, Long userId) {
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (postLikeRepository.deleteByPostIdAndUserId(postId, userId) > 0) {
            postRepository.decreaseLikeCount(postId);
        }
        return new PostLikeResponse(postRepository.findLikeCountById(postId), false);
    }

    private List<PostMedia> saveMedia(Post post, List<PostCreateRequest.Media> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return postMediaRepository.saveAll(requests.stream()
                .map(media -> new PostMedia(post, media.mediaType(), media.url(), media.sortOrder()))
                .toList());
    }

    private List<PostPlaceTag> savePlaceTags(Post post, List<PostCreateRequest.PlaceTag> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Long> placeIds = requests.stream()
                .map(PostCreateRequest.PlaceTag::placeId)
                .distinct()
                .toList();
        Map<Long, Place> places = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        return postPlaceTagRepository.saveAll(requests.stream()
                .map(tag -> {
                    Place place = places.get(tag.placeId());
                    if (place == null) {
                        throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
                    }
                    return new PostPlaceTag(post, place, tag.latitude(), tag.longitude());
                })
                .toList());
    }

    private int resolveFeedSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_FEED_SIZE;
        }
        return Math.min(size, MAX_FEED_SIZE);
    }
}
