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
 */
@Component
@ConditionalOnProperty(
        prefix = "app.community.post-purge",
        name = "enabled",
        havingValue = "true"
)
public class PostPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostPurgeScheduler.class);

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
            int purged = postPurgeService.purgeExpired(retentionDays);
            log.info("게시물 정리를 마쳤다. 지운 수={}, 보관 기간={}일", purged, retentionDays);
        } catch (RuntimeException exception) {
            log.error("게시물 정리에 실패했다. 다음 실행에서 이어서 처리한다.", exception);
        }
    }
}
