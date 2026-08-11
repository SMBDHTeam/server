package com.server.post.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.place.domain.Place;
import com.server.place.repository.PlaceRepository;
import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import com.server.post.domain.PostPlaceTag;
import com.server.post.dto.PostCreateRequest;
import com.server.post.dto.PostDetailResponse;
import com.server.post.dto.PostPlaceTagView;
import com.server.post.dto.PostSummaryListResponse;
import com.server.post.dto.PostSummaryResponse;
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
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;

    public PostService(
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            PostPlaceTagRepository postPlaceTagRepository,
            UserRepository userRepository,
            PlaceRepository placeRepository
    ) {
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.postPlaceTagRepository = postPlaceTagRepository;
        this.userRepository = userRepository;
        this.placeRepository = placeRepository;
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
        List<Post> posts = postRepository.findByDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                cursor == null ? FIRST_PAGE_CURSOR : cursor,
                PageRequest.of(0, limit));

        if (posts.isEmpty()) {
            return new PostSummaryListResponse(List.of(), null);
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, List<PostMedia>> mediaByPost = postMediaRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(media -> media.getPost().getId()));
        Map<Long, List<PostPlaceTagView>> placeTagsByPost = postPlaceTagRepository.findViewsByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(PostPlaceTagView::postId));

        List<PostSummaryResponse> items = posts.stream()
                .map(post -> PostSummaryResponse.from(
                        post,
                        mediaByPost.getOrDefault(post.getId(), List.of()),
                        placeTagsByPost.getOrDefault(post.getId(), List.of())))
                .toList();

        // 요청한 개수를 못 채웠으면 더 가져올 게시물이 없다.
        Long nextCursor = posts.size() < limit ? null : posts.get(posts.size() - 1).getId();
        return new PostSummaryListResponse(items, nextCursor);
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
