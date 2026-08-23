package com.server.bookmark.service;

import com.server.bookmark.domain.Bookmark;
import com.server.bookmark.dto.BookmarkListResponse;
import com.server.bookmark.dto.BookmarkResponse;
import com.server.bookmark.repository.BookmarkRepository;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.post.domain.Post;
import com.server.post.repository.PostRepository;
import com.server.post.service.PostSummaryAssembler;
import com.server.user.domain.User;
import com.server.user.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookmarkService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostSummaryAssembler postSummaryAssembler;

    public BookmarkService(
            BookmarkRepository bookmarkRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            PostSummaryAssembler postSummaryAssembler
    ) {
        this.bookmarkRepository = bookmarkRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postSummaryAssembler = postSummaryAssembler;
    }

    /** 이미 저장한 게시물을 다시 저장해도 중복 생성되지 않는다. */
    @Transactional
    public BookmarkResponse bookmark(Long postId, Long userId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!bookmarkRepository.existsByUserIdAndPostId(userId, postId)) {
            bookmarkRepository.save(new Bookmark(user, post));
        }
        return new BookmarkResponse(true);
    }

    @Transactional
    public BookmarkResponse removeBookmark(Long postId, Long userId) {
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        bookmarkRepository.deleteByUserIdAndPostId(userId, postId);
        return new BookmarkResponse(false);
    }

    /** 저장한 뒤 삭제된 게시물은 목록에서 제외한다. */
    @Transactional(readOnly = true)
    public BookmarkListResponse getMyBookmarks(Long userId, Integer page, Integer size) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        List<Post> posts = bookmarkRepository
                .findByUserIdAndPostDeletedAtIsNullOrderByCreatedAtDesc(userId, pageRequest(page, size))
                .stream()
                .map(Bookmark::getPost)
                .toList();
        return new BookmarkListResponse(postSummaryAssembler.assemble(posts));
    }

    private PageRequest pageRequest(Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(resolvedPage, resolvedSize);
    }
}
