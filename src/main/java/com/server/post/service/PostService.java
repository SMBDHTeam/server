package com.server.post.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.post.domain.Post;
import com.server.post.domain.PostLike;
import com.server.post.domain.PostMedia;
import com.server.post.domain.PostPlaceTag;
import com.server.post.dto.PostCreateRequest;
import com.server.post.dto.PostDetailResponse;
import com.server.post.dto.PostLikeResponse;
import com.server.post.dto.PostPlaceTagView;
import com.server.post.dto.PostSummaryListResponse;
import com.server.post.dto.PostSummaryResponse;
import com.server.post.dto.PostUpdateRequest;
import com.server.post.repository.PostLikeRepository;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostPlaceTagRepository;
import com.server.post.repository.PostRepository;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
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

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostPlaceTagRepository postPlaceTagRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final PostSummaryAssembler postSummaryAssembler;

    public PostService(
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            PostPlaceTagRepository postPlaceTagRepository,
            PostLikeRepository postLikeRepository,
            UserRepository userRepository,
            PlaceRepository placeRepository,
            PostSummaryAssembler postSummaryAssembler
    ) {
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.postPlaceTagRepository = postPlaceTagRepository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.placeRepository = placeRepository;
        this.postSummaryAssembler = postSummaryAssembler;
    }

    @Transactional
    public PostDetailResponse create(Long userId, PostCreateRequest request) {
        User author = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Post post = postRepository.save(new Post(author, request.content()));
        List<PostMedia> mediaList = saveMedia(post, request.mediaList());
        List<PostPlaceTag> placeTags = savePlaceTags(post, request.placeTags());

        return PostDetailResponse.from(
                post,
                mediaList,
                placeTags.stream().map(PostPlaceTagView::from).toList());
    }

    @Transactional(readOnly = true)
    public PostDetailResponse get(Long postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        return PostDetailResponse.from(
                post,
                postMediaRepository.findByPostId(postId),
                postPlaceTagRepository.findViewsByPostId(postId));
    }

    @Transactional(readOnly = true)
    public PostSummaryListResponse getFeed(Long cursor, Integer size) {
        int limit = resolveFeedSize(size);
        return toSummaryList(
                postRepository.findByDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        cursor == null ? FIRST_PAGE_CURSOR : cursor,
                        PageRequest.of(0, limit)),
                limit);
    }

    @Transactional(readOnly = true)
    public PostSummaryListResponse getUserPosts(Long userId, Long cursor, Integer size) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int limit = resolveFeedSize(size);
        return toSummaryList(
                postRepository.findByUserIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        userId,
                        cursor == null ? FIRST_PAGE_CURSOR : cursor,
                        PageRequest.of(0, limit)),
                limit);
    }

    private PostSummaryListResponse toSummaryList(List<Post> posts, int limit) {
        // 요청한 개수를 못 채웠으면 더 가져올 게시물이 없다.
        Long nextCursor = posts.size() < limit ? null : posts.get(posts.size() - 1).getId();
        return new PostSummaryListResponse(postSummaryAssembler.assemble(posts), nextCursor);
    }

    @Transactional
    public PostDetailResponse update(Long postId, Long userId, PostUpdateRequest request) {
        Post post = findWritablePost(postId, userId);
        post.updateContent(request.content());

        return PostDetailResponse.from(
                post,
                postMediaRepository.findByPostId(postId),
                postPlaceTagRepository.findViewsByPostId(postId));
    }

    /**
     * 물리 삭제하지 않고 {@code deletedAt}만 남긴다. 조회 경로가 모두
     * {@code deletedAt IS NULL} 조건을 쓰므로 삭제된 게시물은 응답에 나오지 않는다.
     */
    @Transactional
    public void delete(Long postId, Long userId) {
        findWritablePost(postId, userId).delete();
    }

    private Post findWritablePost(Long postId, Long userId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.POST_ACCESS_DENIED);
        }
        return post;
    }

    /** 이미 눌린 좋아요를 다시 눌러도 개수가 늘지 않는다. */
    @Transactional
    public PostLikeResponse like(Long postId, Long userId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.save(new PostLike(post, user));
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
