package com.server.media.service;

import com.server.media.config.MediaProperties;
import com.server.post.repository.PostMediaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 게시물에 붙지 않은 채 저장소에 남은 파일을 지운다.
 *
 * <p>업로드와 게시물 작성이 호출 두 번으로 나뉘어 있다. 사진을 올려 두고 글을 쓰지 않으면
 * 파일만 저장소에 남고 DB 에는 아무 흔적이 없다. 사진을 골랐다가 바꾸는 것만으로도 생긴다.
 *
 * <p>지울지 판단하려면 저장소에 있는 것과 DB 에 적힌 것을 맞대봐야 한다. "DB 에 없는
 * 주소"는 SQL 로 물을 수 없어서, 저장소 목록을 나눠 들고 와 DB 에 있는 것만 빼는 방식으로
 * 뒤집어 푼다.
 */
@Service
public class OrphanMediaCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OrphanMediaCleanupService.class);

    /**
     * 한 번에 DB 에 물어보는 주소 수.
     *
     * <p>{@code in} 절에 수천 건을 한꺼번에 넣으면 질의 계획이 무너지고 DB 마다 상한도 다르다.
     */
    private static final int LOOKUP_BATCH_SIZE = 500;

    private final PostMediaRepository postMediaRepository;
    private final ObjectProvider<MediaStorage> storageProvider;
    private final MediaProperties properties;

    public OrphanMediaCleanupService(
            PostMediaRepository postMediaRepository,
            ObjectProvider<MediaStorage> storageProvider,
            MediaProperties properties
    ) {
        this.postMediaRepository = postMediaRepository;
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    /** @return 지운 파일 수. 업로드가 꺼져 있으면 0 */
    public int deleteOrphans() {
        MediaStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            // 업로드가 꺼진 환경이다. 지울 파일 자체가 없다.
            return 0;
        }
        return deleteOrphans(storage, properties.orphanCleanup().minAge());
    }

    /** 저장소와 기준 시간을 직접 받는다. 테스트가 가짜 저장소를 넣을 수 있게 열어 둔다. */
    int deleteOrphans(MediaStorage storage, Duration minAge) {
        Instant deadline = Instant.now().minus(minAge);

        // 방금 올라온 파일은 아직 글이 쓰이는 중일 수 있다. 그것까지 지우면 작성하던 사람의
        // 사진이 사라진다. 업로드부터 작성까지 하루가 걸리는 일은 없으므로 여유는 충분하다.
        List<String> candidates = storage.listAll().stream()
                .filter(object -> object.lastModified() != null
                        && object.lastModified().isBefore(deadline))
                .map(MediaStorage.StoredObject::url)
                .toList();
        if (candidates.isEmpty()) {
            return 0;
        }

        Set<String> referenced = referenced(candidates);
        List<String> orphans = candidates.stream()
                .filter(url -> !referenced.contains(url))
                .toList();
        if (orphans.isEmpty()) {
            log.info("저장소를 훑었지만 지울 파일이 없었다. 확인한 파일={}건", candidates.size());
            return 0;
        }

        int failed = 0;
        for (String url : orphans) {
            try {
                storage.delete(url);
            } catch (RuntimeException exception) {
                failed++;
                log.warn("남은 파일을 지우지 못했다. url={}", url, exception);
            }
        }
        int deleted = orphans.size() - failed;
        log.info("게시물에 붙지 않은 파일 {}건을 지웠다. 확인한 파일={}건, 실패={}건",
                deleted, candidates.size(), failed);
        return deleted;
    }

    /** 주어진 주소 중 게시물에 붙어 있는 것. */
    private Set<String> referenced(List<String> urls) {
        Set<String> referenced = new HashSet<>();
        for (int start = 0; start < urls.size(); start += LOOKUP_BATCH_SIZE) {
            List<String> batch = urls.subList(
                    start, Math.min(start + LOOKUP_BATCH_SIZE, urls.size()));
            referenced.addAll(postMediaRepository.findUrlsIn(batch));
        }
        return referenced;
    }
}
