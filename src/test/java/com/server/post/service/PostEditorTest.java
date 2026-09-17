package com.server.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.server.media.service.MediaFileRemover;
import com.server.post.dto.PostDetailResponse;
import com.server.post.dto.PostUpdateRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

@DisplayName("게시물 수정과 파일 정리")
class PostEditorTest {

    private static final long POST_ID = 7L;
    private static final long USER_ID = 1L;

    private final PostService postService = mock(PostService.class);
    private final MediaFileRemover mediaFileRemover = mock(MediaFileRemover.class);
    private final PostEditor postEditor = new PostEditor(postService, mediaFileRemover);

    @Test
    @DisplayName("수정을 마친 뒤에 빠진 사진 파일을 지운다")
    void removesDroppedFilesAfterUpdate() {
        PostDetailResponse response = mock(PostDetailResponse.class);
        List<String> removed = List.of("https://e.com/b.jpg");
        when(postService.update(anyLong(), anyLong(), any()))
                .thenReturn(new PostService.UpdateResult(response, removed));

        assertThat(postEditor.update(POST_ID, USER_ID, new PostUpdateRequest("고침", null, null)))
                .isSameAs(response);

        // 트랜잭션이 끝난 뒤에 지워야 하므로 순서가 바뀌면 안 된다.
        InOrder inOrder = Mockito.inOrder(postService, mediaFileRemover);
        inOrder.verify(postService).update(POST_ID, USER_ID,
                new PostUpdateRequest("고침", null, null));
        inOrder.verify(mediaFileRemover).remove(removed);
    }

    @Test
    @DisplayName("사진을 건드리지 않은 수정은 저장소를 부르지 않는다")
    void skipsStorageWhenNothingDropped() {
        when(postService.update(anyLong(), anyLong(), any()))
                .thenReturn(new PostService.UpdateResult(mock(PostDetailResponse.class), List.of()));

        postEditor.update(POST_ID, USER_ID, new PostUpdateRequest("본문만 고침", null, null));

        verify(mediaFileRemover).remove(List.of());
    }

    @Test
    @DisplayName("수정이 실패하면 파일을 지우지 않는다")
    void keepsFilesWhenUpdateFails() {
        when(postService.update(anyLong(), anyLong(), any()))
                .thenThrow(new IllegalStateException("수정 실패"));

        try {
            postEditor.update(POST_ID, USER_ID, new PostUpdateRequest("고침", null, null));
        } catch (IllegalStateException ignored) {
            // 예외는 그대로 올라간다.
        }

        verifyNoInteractions(mediaFileRemover);
    }
}
