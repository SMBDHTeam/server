package com.server.post.scheduler;

import com.server.post.service.PostPurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 복구 기한이 지난 게시물을 주기적으로 지운다. 실패해도 다음 실행이 이어서 처리하도록
 * 예외를 삼키고 로그만 남긴다. 스케줄러가 죽으면 이후 정리가 통째로 멈추기 때문이다.
 *
 * <p>정리는 한 번에 한 묶음씩 지우므로, 남은 분량이 없을 때까지 반복해서 부른다.
 * 한 번만 부르면 하루 만료량이 묶음 크기를 넘을 때 매일 밀린 양이 늘어난다.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.community.post-purge",
        name = "enabled",
        havingValue = "true"
)
public class PostPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostPurgeScheduler.class);

    /**
     * 한 번 실행에서 처리할 최대 묶음 수. 예상치 못한 이유로 삭제가 반영되지 않으면 같은
     * 묶음을 무한히 다시 읽게 되므로 상한을 둔다.
     */
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final PostPurgeService postPurgeService;
    private final int retentionDays;

    public PostPurgeScheduler(
            PostPurgeService postPurgeService,
            @Value("${app.community.post-purge.retention-days}") int retentionDays
    ) {
        this.postPurgeService = postPurgeService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.community.post-purge.cron}", zone = "Asia/Seoul")
    public void purgeExpiredPosts() {
        try {
            int totalPurged = 0;
            for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                int purged = postPurgeService.purgeExpired(retentionDays);
                totalPurged += purged;
                if (purged < PostPurgeService.BATCH_SIZE) {
                    log.info("게시물 정리를 마쳤다. 지운 수={}, 보관 기간={}일",
                            totalPurged, retentionDays);
                    return;
                }
            }
            // 상한까지 돌고도 남았다면 다음 실행을 기다리지 말고 알아챌 수 있어야 한다.
            log.warn("게시물 정리가 한 번에 끝나지 않았다. 지운 수={}, 남은 분량은 다음 실행에서 처리한다.",
                    totalPurged);
        } catch (RuntimeException exception) {
            log.error("게시물 정리에 실패했다. 다음 실행에서 이어서 처리한다.", exception);
        }
    }
}
