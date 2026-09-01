package com.server.post.scheduler;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.media.service.MediaStorage;
import com.server.post.service.PostPurgeService;
import com.server.post.service.PostPurgeService.PurgeResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 정리는 한 번에 한 묶음씩만 지운다. 스케줄러가 한 번만 부르면 하루 만료량이 묶음 크기를
 * 넘을 때 매일 밀린 양이 늘어나므로, 남은 분량이 없을 때까지 반복하는지 고정한다.
 *
 * <p>저장소의 파일을 함께 지우는지도 여기서 본다. 게시물만 지우고 파일을 남기면 버킷에
 * 아무도 참조하지 않는 사진이 쌓인다.
 */
@DisplayName("게시물 정리 스케줄러")
class PostPurgeSchedulerTest {

    private static final int RETENTION_DAYS = 30;

    private final PostPurgeService postPurgeService = Mockito.mock(PostPurgeService.class);
    private final MediaStorage mediaStorage = Mockito.mock(MediaStorage.class);

    private final PostPurgeScheduler scheduler =
            new PostPurgeScheduler(postPurgeService, providerOf(mediaStorage), RETENTION_DAYS);

    @Test
    @DisplayName("묶음이 가득 차면 남은 분량이 없을 때까지 이어서 부른다")
    void repeatsUntilBatchIsNotFull() {
        when(postPurgeService.purgeExpired(RETENTION_DAYS))
                .thenReturn(full(), full(), purged(7));

        scheduler.purgeExpiredPosts();

        verify(postPurgeService, times(3)).purgeExpired(RETENTION_DAYS);
    }

    @Test
    @DisplayName("지울 게 없으면 한 번만 부른다")
    void stopsWhenNothingToPurge() {
        when(postPurgeService.purgeExpired(RETENTION_DAYS)).thenReturn(purged(0));

        scheduler.purgeExpiredPosts();

        verify(postPurgeService, times(1)).purgeExpired(RETENTION_DAYS);
    }

    @Test
    @DisplayName("지운 게시물의 파일도 저장소에서 지운다")
    void deletesMediaFiles() {
        when(postPurgeService.purgeExpired(RETENTION_DAYS))
                .thenReturn(new PurgeResult(2, List.of("https://bucket/a.jpg", "https://bucket/b.jpg")));

        scheduler.purgeExpiredPosts();

        verify(mediaStorage).delete("https://bucket/a.jpg");
        verify(mediaStorage).delete("https://bucket/b.jpg");
    }

    @Test
    @DisplayName("파일 하나를 못 지워도 나머지를 이어서 지운다")
    void keepsGoingWhenOneFileFails() {
        when(postPurgeService.purgeExpired(RETENTION_DAYS))
                .thenReturn(new PurgeResult(2, List.of("https://bucket/a.jpg", "https://bucket/b.jpg")));
        Mockito.doThrow(new IllegalStateException("저장소 오류"))
                .when(mediaStorage).delete("https://bucket/a.jpg");

        // 한 건 때문에 멈추면 DB 는 지워진 채 다음 묶음이 밀린다.
        scheduler.purgeExpiredPosts();

        verify(mediaStorage).delete("https://bucket/b.jpg");
    }

    @Test
    @DisplayName("저장소가 꺼져 있어도 정리는 돈다")
    void runsWithoutStorage() {
        PostPurgeScheduler withoutStorage =
                new PostPurgeScheduler(postPurgeService, providerOf(null), RETENTION_DAYS);
        when(postPurgeService.purgeExpired(RETENTION_DAYS))
                .thenReturn(new PurgeResult(1, List.of("https://bucket/a.jpg")));

        withoutStorage.purgeExpiredPosts();

        verify(postPurgeService).purgeExpired(RETENTION_DAYS);
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
        when(postPurgeService.purgeExpired(RETENTION_DAYS)).thenReturn(full());

        scheduler.purgeExpiredPosts();

        verify(postPurgeService, times(100)).purgeExpired(RETENTION_DAYS);
        verify(postPurgeService, never()).purgeExpired(0);
    }

    private static PurgeResult full() {
        return purged(PostPurgeService.BATCH_SIZE);
    }

    private static PurgeResult purged(int count) {
        return new PurgeResult(count, List.of());
    }

    /** {@code null} 을 주면 업로드가 꺼진 환경을 흉내 낸다. */
    private static ObjectProvider<MediaStorage> providerOf(MediaStorage storage) {
        return new ObjectProvider<>() {
            @Override
            public MediaStorage getObject() {
                return storage;
            }

            @Override
            public MediaStorage getIfAvailable() {
                return storage;
            }
        };
    }
}
