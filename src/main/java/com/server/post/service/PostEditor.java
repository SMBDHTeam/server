package com.server.post.service;

import com.server.media.service.MediaFileRemover;
import com.server.post.dto.PostDetailResponse;
import com.server.post.dto.PostUpdateRequest;
import org.springframework.stereotype.Service;

/**
 * 게시물 수정과 그에 딸린 파일 정리를 잇는다.
 *
 * <p><b>일부러 트랜잭션을 걸지 않는다.</b> 수정은 {@link PostService#update} 의 트랜잭션에서
 * 끝내고, 저장소 삭제는 그 트랜잭션이 완전히 끝나 DB 커넥션이 반납된 뒤에 한다. 삭제를
 * 트랜잭션 안에서 하면 저장소를 기다리는 동안 커넥션을 쥐고 있어 동시 요청이 늘 때 풀이
 * 마른다. 만료 게시물 정리도 트랜잭션에서 지울 주소만 받아 와 스케줄러에서 지운다.
 *
 * <p>파일을 못 지워도 수정은 이미 커밋됐고 사용자에게는 성공이다. {@link MediaFileRemover}
 * 가 건별로 삼키고 로그만 남긴다.
 */
@Service
public class PostEditor {

    private final PostService postService;
    private final MediaFileRemover mediaFileRemover;

    public PostEditor(PostService postService, MediaFileRemover mediaFileRemover) {
        this.postService = postService;
        this.mediaFileRemover = mediaFileRemover;
    }

    public PostDetailResponse update(Long postId, Long userId, PostUpdateRequest request) {
        PostService.UpdateResult result = postService.update(postId, userId, request);
        mediaFileRemover.remove(result.removedMediaUrls());
        return result.response();
    }
}
