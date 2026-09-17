package com.server.media.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 더 이상 쓰지 않는 파일을 저장소에서 지운다.
 *
 * <p>트랜잭션이 열려 있으면 커밋된 뒤에 지운다. 트랜잭션 안에서 지우면 뒤이어 롤백됐을 때
 * 파일만 사라지고 DB 는 그대로 남아, 사진이 깨진 게시물이 된다. 정리 스케줄러가 DB 를
 * 커밋한 뒤에 파일을 지우는 것과 같은 이유다.
 *
 * <p>파일 하나를 못 지웠다고 부르는 쪽을 실패시키지 않는다. 사용자가 보기에 수정은 이미
 * 끝났고, 남은 파일은 비용일 뿐 화면에 영향을 주지 않는다. 건별로 삼키고 로그만 남긴다.
 */
@Component
public class MediaFileRemover {

    private static final Logger log = LoggerFactory.getLogger(MediaFileRemover.class);

    private final ObjectProvider<MediaStorage> mediaStorageProvider;

    public MediaFileRemover(ObjectProvider<MediaStorage> mediaStorageProvider) {
        this.mediaStorageProvider = mediaStorageProvider;
    }

    /** 트랜잭션이 열려 있으면 커밋 뒤에, 아니면 곧바로 지운다. */
    public void removeAfterCommit(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<String> targets = List.copyOf(urls);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            remove(targets);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                remove(targets);
            }
        });
    }

    /** 저장소가 꺼져 있으면 아무것도 하지 않는다. 그때는 우리가 올린 파일 자체가 없다. */
    private void remove(List<String> urls) {
        MediaStorage storage = mediaStorageProvider.getIfAvailable();
        if (storage == null) {
            return;
        }
        int failed = 0;
        for (String url : urls) {
            try {
                storage.delete(url);
            } catch (RuntimeException exception) {
                failed++;
                log.warn("쓰지 않는 파일을 지우지 못했다. url={}", url, exception);
            }
        }
        log.info("쓰지 않는 파일 {}건을 지웠다. 실패={}", urls.size() - failed, failed);
    }
}
