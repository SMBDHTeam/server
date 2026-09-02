package com.server.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.post.domain.MediaType;
import com.server.post.domain.Post;
import com.server.post.domain.PostMedia;
import com.server.post.repository.PostMediaRepository;
import com.server.user.domain.User;
import jakarta.persistence.EntityManager;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장소에는 있는데 게시물에 붙지 않은 파일만 지우는지 고정한다. 판단을 잘못하면 남의
 * 게시물 사진이 사라지므로, "안 지운다"는 쪽을 더 촘촘히 본다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("게시물에 붙지 않은 파일 정리")
class OrphanMediaCleanupServiceTest {

    private static final Duration MIN_AGE = Duration.ofHours(24);
    private static final String BASE = "https://bucket.s3.ap-northeast-2.amazonaws.com/posts/";

    @Autowired
    private OrphanMediaCleanupService cleanupService;

    @Autowired
    private PostMediaRepository postMediaRepository;

    @Autowired
    private EntityManager entityManager;

    private FakeStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FakeStorage();
    }

    @Test
    @DisplayName("게시물에 붙지 않은 오래된 파일을 지운다")
    void deletesOrphan() {
        storage.put(BASE + "orphan.jpg", daysAgo(3));

        assertThat(cleanupService.deleteOrphans(storage, MIN_AGE)).isEqualTo(1);
        assertThat(storage.deleted).containsExactly(BASE + "orphan.jpg");
    }

    @Test
    @DisplayName("게시물에 붙은 파일은 아무리 오래돼도 두고 본다")
    void keepsReferenced() {
        String used = BASE + "used.jpg";
        givenPostWithMedia(used);
        storage.put(used, daysAgo(365));

        assertThat(cleanupService.deleteOrphans(storage, MIN_AGE)).isZero();
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    @DisplayName("방금 올라온 파일은 아직 글을 쓰는 중일 수 있어 건드리지 않는다")
    void keepsRecentUpload() {
        storage.put(BASE + "just-uploaded.jpg", Instant.now().minus(Duration.ofMinutes(10)));

        assertThat(cleanupService.deleteOrphans(storage, MIN_AGE)).isZero();
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    @DisplayName("붙은 것과 안 붙은 것이 섞여 있으면 안 붙은 것만 지운다")
    void deletesOnlyOrphans() {
        String used = BASE + "used.jpg";
        givenPostWithMedia(used);
        storage.put(used, daysAgo(3));
        storage.put(BASE + "orphan.jpg", daysAgo(3));

        assertThat(cleanupService.deleteOrphans(storage, MIN_AGE)).isEqualTo(1);
        assertThat(storage.deleted).containsExactly(BASE + "orphan.jpg");
    }

    @Test
    @DisplayName("하나를 못 지워도 나머지를 이어서 지운다")
    void keepsGoingWhenOneFails() {
        storage.put(BASE + "broken.jpg", daysAgo(3));
        storage.put(BASE + "orphan.jpg", daysAgo(3));
        storage.failOn = BASE + "broken.jpg";

        assertThat(cleanupService.deleteOrphans(storage, MIN_AGE)).isEqualTo(1);
        assertThat(storage.deleted).containsExactly(BASE + "orphan.jpg");
    }

    @Test
    @DisplayName("게시물에 http 로 적혀 있어도 쓰이는 파일로 본다")
    void keepsReferencedWithDifferentScheme() {
        // 저장소는 https 로 주는데 게시물에는 http 로 적혀 있을 수 있다. 글자 그대로
        // 맞대면 쓰이고 있는 사진을 지운다.
        String https = BASE + "used.jpg";
        givenPostWithMedia(https.replace("https://", "http://"));
        storage.put(https, daysAgo(3));

        assertThat(cleanupService.deleteOrphans(storage, MIN_AGE)).isZero();
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    @DisplayName("여러 페이지에 걸쳐 있어도 붙지 않은 것만 지운다")
    void worksAcrossPages() {
        // 저장소는 목록을 나눠서 준다. 페이지마다 처리하므로 경계에서 새지 않아야 한다.
        String used = BASE + "used.jpg";
        givenPostWithMedia(used);
        for (int index = 0; index < 5; index++) {
            storage.put(BASE + "orphan" + index + ".jpg", daysAgo(3));
        }
        storage.put(used, daysAgo(3));

        assertThat(cleanupService.deleteOrphans(storage, MIN_AGE)).isEqualTo(5);
        assertThat(storage.deleted).hasSize(5).doesNotContain(used);
    }

    private void givenPostWithMedia(String url) {
        User author = new User("작성자", null);
        entityManager.persist(author);
        Post post = new Post(author, "사진 있는 글");
        entityManager.persist(post);
        postMediaRepository.save(new PostMedia(post, MediaType.IMAGE, url, 0));
        entityManager.flush();
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    /** 저장소를 흉내 낸다. 지운 목록을 기록해 무엇이 사라졌는지 확인한다. */
    private static final class FakeStorage implements MediaStorage {

        private static final int PAGE_SIZE = 2;

        private final List<StoredObject> objects = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private String failOn;

        void put(String url, Instant lastModified) {
            objects.add(new StoredObject(url, lastModified));
        }

        @Override
        public String upload(String key, String contentType, InputStream content, long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String url) {
            if (url.equals(failOn)) {
                throw new IllegalStateException("저장소 오류");
            }
            deleted.add(url);
        }

        /** 실제 S3 처럼 페이지를 나눠 넘긴다. 여기서는 두 건씩이다. */
        @Override
        public void forEachPage(Consumer<List<StoredObject>> pageConsumer) {
            for (int start = 0; start < objects.size(); start += PAGE_SIZE) {
                pageConsumer.accept(List.copyOf(
                        objects.subList(start, Math.min(start + PAGE_SIZE, objects.size()))));
            }
        }
    }
}
