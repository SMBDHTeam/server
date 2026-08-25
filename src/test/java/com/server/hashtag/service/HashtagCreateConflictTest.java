package com.server.hashtag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.hashtag.repository.HashtagRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 태그를 두 사람이 동시에 처음 쓰는 상황이다. 확인 후 저장하면 한쪽이 이름 고유
 * 제약에 걸려 실패하므로, 넣어 보고 충돌을 무시하는지 실제 DB 로 확인한다.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("해시태그 생성 중복")
class HashtagCreateConflictTest {

    private static final String NEW_TAG = "처음쓰는태그";

    @Autowired
    private HashtagRepository hashtagRepository;

    @Test
    @DisplayName("같은 이름을 두 번 넣어도 실패하지 않고 한 건만 남는다")
    void insertTwiceKeepsOne() {
        hashtagRepository.insertIfAbsent(NEW_TAG);
        hashtagRepository.insertIfAbsent(NEW_TAG);

        assertThat(hashtagRepository.findByNameIn(List.of(NEW_TAG))).hasSize(1);
    }
}
