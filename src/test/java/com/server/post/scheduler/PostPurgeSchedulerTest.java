package com.server.post.scheduler;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.post.service.PostPurgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 정리는 한 번에 한 묶음씩만 지운다. 스케줄러가 한 번만 부르면 하루 만료량이 묶음 크기를
 * 넘을 때 매일 밀린 양이 늘어나므로, 남은 분량이 없을 때까지 반복하는지 고정한다.
 */
@DisplayName("게시물 정리 스케줄러")
class PostPurgeSchedulerTest {

    private static final int RETENTION_DAYS = 30;

    private final PostPurgeService postPurgeService = Mockito.mock(PostPurgeService.class);

    private final PostPurgeScheduler scheduler =
            new PostPurgeScheduler(postPurgeService, RETENTION_DAYS);

    @Test
    @DisplayName("묶음이 가득 차면 남은 분량이 없을 때까지 이어서 부른다")
    void repeatsUntilBatchIsNotFull() {
        when(postPurgeService.purgeExpired(RETENTION_DAYS))
                .thenReturn(PostPurgeService.BATCH_SIZE, PostPurgeService.BATCH_SIZE, 7);

        scheduler.purgeExpiredPosts();

        verify(postPurgeService, times(3)).purgeExpired(RETENTION_DAYS);
    }

    @Test
    @DisplayName("지울 게 없으면 한 번만 부른다")
    void stopsWhenNothingToPurge() {
        when(postPurgeService.purgeExpired(RETENTION_DAYS)).thenReturn(0);

        scheduler.purgeExpiredPosts();

        verify(postPurgeService, times(1)).purgeExpired(RETENTION_DAYS);
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않는다")
    void swallowsFailure() {
        when(postPurgeService.purgeExpired(anyInt()))
                .thenThrow(new IllegalStateException("DB 연결 실패"));

        // 스케줄러가 예외로 죽으면 이후 정리가 통째로 멈춘다.
        scheduler.purgeExpiredPosts();
    }

    @Test
    @DisplayName("상한을 넘어서까지 무한히 돌지 않는다")
    void stopsAtBatchLimit() {
        // 삭제가 반영되지 않아 같은 묶음을 계속 읽는 상황이다.
        when(postPurgeService.purgeExpired(RETENTION_DAYS))
                .thenReturn(PostPurgeService.BATCH_SIZE);

        scheduler.purgeExpiredPosts();

        verify(postPurgeService, times(100)).purgeExpired(RETENTION_DAYS);
        verify(postPurgeService, never()).purgeExpired(0);
    }
}
