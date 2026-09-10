package com.server.common.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("상대 시간 문구")
class RelativeTimeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 15, 0, 0);

    @Test
    @DisplayName("1분 미만은 초로 센다")
    void seconds() {
        assertThat(ago(NOW)).isEqualTo("0초 전");
        assertThat(ago(NOW.minusSeconds(30))).isEqualTo("30초 전");
        assertThat(ago(NOW.minusSeconds(59))).isEqualTo("59초 전");
    }

    @Test
    @DisplayName("1분부터 1시간 전까지는 분으로 센다")
    void minutes() {
        assertThat(ago(NOW.minusSeconds(60))).isEqualTo("1분 전");
        assertThat(ago(NOW.minusMinutes(59))).isEqualTo("59분 전");
    }

    @Test
    @DisplayName("1시간부터 하루 전까지는 시간으로 센다")
    void hours() {
        assertThat(ago(NOW.minusHours(1))).isEqualTo("1시간 전");
        assertThat(ago(NOW.minusHours(23))).isEqualTo("23시간 전");
    }

    @Test
    @DisplayName("하루부터 한 달 전까지는 일로 센다")
    void days() {
        assertThat(ago(NOW.minusDays(1))).isEqualTo("1일 전");
        // 달을 30일로 고정하지 않고 달력으로 세므로, 8월 9일은 아직 한 달이 안 됐다.
        assertThat(ago(NOW.minusDays(30))).isEqualTo("30일 전");
    }

    @Test
    @DisplayName("한 달부터 한 해 전까지는 달로 센다")
    void months() {
        assertThat(ago(NOW.minusMonths(1))).isEqualTo("1달 전");
        assertThat(ago(NOW.minusDays(364))).isEqualTo("11달 전");
    }

    @Test
    @DisplayName("한 해가 넘으면 해로 센다")
    void years() {
        assertThat(ago(NOW.minusDays(365))).isEqualTo("1년 전");
        assertThat(ago(NOW.minusYears(3))).isEqualTo("3년 전");
    }

    @Test
    @DisplayName("미래 시각은 방금으로 본다")
    void future() {
        // 서버 시계가 뒤로 조정되면 저장 시각이 지금보다 앞설 수 있다. "-3초 전" 이 화면에
        // 뜨는 것보다 낫다.
        assertThat(ago(NOW.plusSeconds(3))).isEqualTo("0초 전");
    }

    @Test
    @DisplayName("시각이 없으면 문구도 없다")
    void nullTime() {
        assertThat(RelativeTime.from(null, NOW)).isNull();
    }

    private String ago(LocalDateTime time) {
        return RelativeTime.from(time, NOW);
    }
}
