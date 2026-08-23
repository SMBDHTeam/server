package com.server.post.service;

import com.server.bookmark.repository.BookmarkRepository;
import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import com.server.post.dto.PostPlaceTagView;
import com.server.post.dto.PostSummaryResponse;
import com.server.post.repository.PostLikeRepository;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostPlaceTagRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 게시물 목록에 미디어·장소 태그와 요청자의 좋아요·저장 여부를 붙여 축약 응답으로 만든다.
 * 게시물 수와 무관하게 각 정보를 한 번씩만 읽는다.
 */
@Component
public class PostSummaryAssembler {

    private final PostMediaRepository postMediaRepository;
    private final PostPlaceTagRepository postPlaceTagRepository;
    private final PostLikeRepository postLikeRepository;
    private final BookmarkRepository bookmarkRepository;

    public PostSummaryAssembler(
            PostMediaRepository postMediaRepository,
            PostPlaceTagRepository postPlaceTagRepository,
            PostLikeRepository postLikeRepository,
            BookmarkRepository bookmarkRepository
    ) {
        this.postMediaRepository = postMediaRepository;
        this.postPlaceTagRepository = postPlaceTagRepository;
        this.postLikeRepository = postLikeRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    /**
     * @param requesterId 조회하는 사용자. 인증이 없어 전달되지 않을 수 있으며,
     *                    없으면 좋아요·저장 여부를 모두 false 로 둔다.
     */
    public List<PostSummaryResponse> assemble(List<Post> posts, Long requesterId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, List<PostMedia>> mediaByPost = postMediaRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(media -> media.getPost().getId()));
        Map<Long, List<PostPlaceTagView>> placeTagsByPost =
                postPlaceTagRepository.findViewsByPostIdIn(postIds).stream()
                        .collect(Collectors.groupingBy(PostPlaceTagView::postId));
        Set<Long> likedPostIds = likedPostIds(requesterId, postIds);
        Set<Long> bookmarkedPostIds = bookmarkedPostIds(requesterId, postIds);

        return posts.stream()
                .map(post -> PostSummaryResponse.from(
                        post,
                        mediaByPost.getOrDefault(post.getId(), List.of()),
                        placeTagsByPost.getOrDefault(post.getId(), List.of()),
                        likedPostIds.contains(post.getId()),
                        bookmarkedPostIds.contains(post.getId())))
                .toList();
    }

    public Set<Long> likedPostIds(Long requesterId, List<Long> postIds) {
        if (requesterId == null || postIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(postLikeRepository.findLikedPostIds(requesterId, postIds));
    }

    public Set<Long> bookmarkedPostIds(Long requesterId, List<Long> postIds) {
        if (requesterId == null || postIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(bookmarkRepository.findBookmarkedPostIds(requesterId, postIds));
    }
}
