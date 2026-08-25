package com.server.post.service;

import com.server.bookmark.repository.BookmarkRepository;
import com.server.hashtag.repository.PostHashtagRepository;
import com.server.post.domain.Post;
import com.server.post.repository.CommentLikeRepository;
import com.server.post.repository.CommentRepository;
import com.server.post.repository.PostLikeRepository;
import com.server.post.repository.PostMediaRepository;
import com.server.post.repository.PostPlaceTagRepository;
import com.server.post.repository.PostRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복구 기한이 지난 게시물을 실제로 지운다. 소프트 삭제만 하면 지운 글이 영원히 쌓이고,
 * 사용자가 지웠다고 믿는 본문과 사진 URL 이 DB 에 그대로 남는다.
 *
 * <p>게시물을 참조하는 행을 먼저 지운다. 외래키가 걸려 있어 순서를 지키지 않으면 실패한다.
 */
@Service
public class PostPurgeService {

    private static final Logger log = LoggerFactory.getLogger(PostPurgeService.class);

    /** 한 번에 지우는 최대 건수. 한 트랜잭션이 지나치게 길어지지 않게 나눠 지운다. */
    private static final int BATCH_SIZE = 100;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostPlaceTagRepository postPlaceTagRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final BookmarkRepository bookmarkRepository;

    public PostPurgeService(
            PostRepository postRepository,
            PostMediaRepository postMediaRepository,
            PostPlaceTagRepository postPlaceTagRepository,
            PostLikeRepository postLikeRepository,
            PostHashtagRepository postHashtagRepository,
            CommentRepository commentRepository,
            CommentLikeRepository commentLikeRepository,
            BookmarkRepository bookmarkRepository
    ) {
        this.postRepository = postRepository;
        this.postMediaRepository = postMediaRepository;
        this.postPlaceTagRepository = postPlaceTagRepository;
        this.postLikeRepository = postLikeRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    /**
     * 삭제한 지 {@code retentionDays} 일이 지난 게시물을 지운다.
     *
     * @return 지운 게시물 수
     */
    @Transactional
    public int purgeExpired(int retentionDays) {
        LocalDateTime deadline = LocalDateTime.now().minusDays(retentionDays);
        List<Post> expired = postRepository.findDeletedBefore(
                deadline, PageRequest.of(0, BATCH_SIZE));
        if (expired.isEmpty()) {
            return 0;
        }

        expired.forEach(post -> purge(post.getId()));
        postRepository.deleteAll(expired);
        log.info("복구 기한이 지난 게시물 {}건을 지웠다. 기준 시각={}", expired.size(), deadline);
        return expired.size();
    }

    private void purge(Long postId) {
        // 댓글 좋아요가 댓글을 참조하므로 댓글보다 먼저 지운다.
        commentLikeRepository.deleteByPostId(postId);
        commentRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        bookmarkRepository.deleteByPostId(postId);
        postMediaRepository.deleteByPostId(postId);
        postPlaceTagRepository.deleteByPostId(postId);
        // 삭제 시점에 이미 끊었지만, 예전 데이터가 남아 있을 수 있어 한 번 더 지운다.
        postHashtagRepository.deleteByPostId(postId);
    }
}
