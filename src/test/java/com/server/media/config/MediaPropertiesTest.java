package com.server.media.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("미디어 설정")
class MediaPropertiesTest {

    @Test
    @DisplayName("정리 기준 시간이 비어 있으면 24시간으로 채운다")
    void fillsMissingMinAge() {
        // 빈 값을 그대로 두면 정리 도중 기준 시각을 계산하다 터진다. 그렇다고 "제한 없음"
        // 으로 읽으면 방금 올라온 파일까지 지운다.
        MediaProperties.OrphanCleanup cleanup = new MediaProperties.OrphanCleanup(true, null);

        assertThat(cleanup.minAge()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("설정한 값은 그대로 쓴다")
    void keepsConfiguredMinAge() {
        MediaProperties.OrphanCleanup cleanup =
                new MediaProperties.OrphanCleanup(true, Duration.ofHours(6));

        assertThat(cleanup.minAge()).isEqualTo(Duration.ofHours(6));
    }
}
