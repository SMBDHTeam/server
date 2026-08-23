package com.server.post.service;

import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import com.server.post.dto.PostPlaceTagView;
import com.server.post.dto.PostSummaryResponse;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostPlaceTagRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 게시물 목록에 미디어와 장소 태그를 붙여 축약 응답으로 만든다.
 * 게시물 수와 무관하게 미디어·장소 태그를 각각 한 번씩만 읽는다.
 */
@Component
public class PostSummaryAssembler {

    private final PostMediaRepository postMediaRepository;
    private final PostPlaceTagRepository postPlaceTagRepository;

    public PostSummaryAssembler(
            PostMediaRepository postMediaRepository,
            PostPlaceTagRepository postPlaceTagRepository
    ) {
        this.postMediaRepository = postMediaRepository;
        this.postPlaceTagRepository = postPlaceTagRepository;
    }

    public List<PostSummaryResponse> assemble(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, List<PostMedia>> mediaByPost = postMediaRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(media -> media.getPost().getId()));
        Map<Long, List<PostPlaceTagView>> placeTagsByPost =
                postPlaceTagRepository.findViewsByPostIdIn(postIds).stream()
                        .collect(Collectors.groupingBy(PostPlaceTagView::postId));

        return posts.stream()
                .map(post -> PostSummaryResponse.from(
                        post,
                        mediaByPost.getOrDefault(post.getId(), List.of()),
                        placeTagsByPost.getOrDefault(post.getId(), List.of())))
                .toList();
    }
}
