package com.server.common.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장되는 시각이 서버가 도는 시간대에 흔들리지 않아야 한다.
 *
 * <p>흔들리던 시절에 실제로 겪은 일이 있다. 통계는 한국 날짜로 자르는데 데이터는 서버
 * 시계로 저장돼서, UTC 로 도는 GitHub Actions 에서만 한국 시각 0~9시에 하루가 어긋났다.
 */
@DisplayName("서버 시계")
class ServerClockTest {

    @Test
    @DisplayName("시스템 시간대와 무관하게 한국 시각을 준다")
    void staysInKoreaRegardlessOfSystemZone() {
        LocalDateTime korea = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        assertThat(Duration.between(korea, ServerClock.now()).abs())
                .isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("UTC 로 읽은 값과 아홉 시간 차이가 난다")
    void differsFromUtcByNineHours() {
        // 이 차이를 눈치채지 못한 채 한쪽만 한국으로 고정해 두었던 것이 원인이었다.
        LocalDateTime utc = LocalDateTime.now(ZoneId.of("UTC"));

        assertThat(Duration.between(utc, ServerClock.now()).toHours()).isEqualTo(9);
    }
}
