package com.server.media.scheduler;

import com.server.media.service.OrphanMediaCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 게시물에 붙지 않은 파일을 주기적으로 지운다.
 *
 * <p>게시물 정리와 나눠 둔 이유가 있다. 그쪽은 지울 대상이 DB 에 있어 정확하지만, 이쪽은
 * 저장소 전체를 훑어 맞대보는 작업이라 훨씬 무겁다. 주기와 실패 영향이 달라야 한다.
 *
 * <p>실패해도 예외를 삼킨다. 지우지 못한 파일은 다음 실행에서 다시 후보가 된다.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.media.orphan-cleanup",
        name = "enabled",
        havingValue = "true"
)
public class OrphanMediaPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrphanMediaPurgeScheduler.class);

    private final OrphanMediaCleanupService cleanupService;

    public OrphanMediaPurgeScheduler(OrphanMediaCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${app.media.orphan-cleanup.cron}", zone = "Asia/Seoul")
    public void purgeOrphans() {
        try {
            cleanupService.deleteOrphans();
        } catch (RuntimeException exception) {
            log.error("남은 파일 정리에 실패했다. 다음 실행에서 이어서 처리한다.", exception);
        }
    }
}
