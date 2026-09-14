package com.server.common.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
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
        // 시간대를 바꾸지 않고 비교하면, 개발자 맥북처럼 이미 한국인 환경에서는 잘못 짠
        // 코드도 통과한다. 실제로 바꿔 봐야 시간대를 고정했는지 알 수 있다.
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

            LocalDateTime korea = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

            assertThat(Duration.between(korea, ServerClock.now()).abs())
                    .isLessThan(Duration.ofSeconds(5));
        } finally {
            // 되돌리지 않으면 뒤따라 도는 테스트가 뉴욕 시계로 실행된다.
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("UTC 로 읽은 값과 아홉 시간 차이가 난다")
    void differsFromUtcByNineHours() {
        // 이 차이를 눈치채지 못한 채 한쪽만 한국으로 고정해 두었던 것이 원인이었다.
        LocalDateTime utc = LocalDateTime.now(ZoneId.of("UTC"));

        assertThat(Duration.between(utc, ServerClock.now()).toHours()).isEqualTo(9);
    }
}
