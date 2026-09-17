package com.server.media.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 더 이상 쓰지 않는 파일을 저장소에서 지운다.
 *
 * <p><b>트랜잭션 밖에서 불러야 한다.</b> 트랜잭션 안에서 부르면 저장소 응답을 기다리는 동안
 * DB 커넥션을 쥐고 있게 되어, 동시 요청이 늘면 커넥션 풀이 마른다. 커밋 직후에 도는
 * {@code TransactionSynchronization.afterCommit} 도 커넥션이 반납되기 전이라 마찬가지다.
 * 만료 게시물 정리가 트랜잭션에서 지울 주소만 받아 와 스케줄러에서 지우는 것과 같은 이유다.
 *
 * <p>파일 하나를 못 지웠다고 부르는 쪽을 실패시키지 않는다. 사용자가 보기에 작업은 이미
 * 끝났고, 남은 파일은 비용일 뿐 화면에 영향을 주지 않는다. 건별로 삼키고 로그만 남긴다.
 *
 * <p>삭제는 부른 스레드에서 그대로 한다. 저장소를 기다리는 만큼 응답도 늦지만, 한 게시물의
 * 사진이 열 장 이하이고 그중 빠진 것만 지우므로 지금은 감수할 만하다. 알림 SSE 발송도 요청
 * 스레드에서 보낸다. 별도 스레드로 옮길 때는 풀 크기와 큐가 찼을 때의 처리, 종료할 때 남은
 * 작업을 함께 정해야 하므로 알림과 기준을 맞춰 한 번에 옮긴다.
 */
@Component
public class MediaFileRemover {

    private static final Logger log = LoggerFactory.getLogger(MediaFileRemover.class);

    private final ObjectProvider<MediaStorage> mediaStorageProvider;

    public MediaFileRemover(ObjectProvider<MediaStorage> mediaStorageProvider) {
        this.mediaStorageProvider = mediaStorageProvider;
    }

    /** 저장소가 꺼져 있으면 아무것도 하지 않는다. 그때는 우리가 올린 파일 자체가 없다. */
    public void remove(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
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
