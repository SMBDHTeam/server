package com.server.common.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * "5분 전" 처럼 지금으로부터 얼마나 지났는지를 문구로 만든다.
 *
 * <p>클라이언트가 직접 계산하지 못하는 것은 응답의 시각에 시간대가 없기 때문이다.
 * {@code createdAt} 은 {@code LocalDateTime} 이라 {@code "2026-09-08T14:27:46"} 으로만
 * 나가고, 이것이 어느 지역 시각인지 적혀 있지 않다. 클라이언트가 자기 시계로 빼면 서버가
 * 다른 시간대로 도는 만큼 통째로 어긋난다. 서버에서 같은 시계끼리 빼면 그 문제가 없다.
 */
public final class RelativeTime {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;


    private RelativeTime() {
    }

    public static String from(LocalDateTime time) {
        return from(time, LocalDateTime.now());
    }

    /** @param now 테스트에서 기준 시각을 고정할 때 쓴다. */
    static String from(LocalDateTime time, LocalDateTime now) {
        if (time == null) {
            return null;
        }
        long seconds = Duration.between(time, now).getSeconds();
        // 서버 시계가 뒤로 조정되거나 저장 시각이 미세하게 앞설 수 있다. "-3초 전" 대신
        // 방금으로 본다.
        if (seconds < 0) {
            seconds = 0;
        }
        if (seconds < MINUTE) {
            return seconds + "초 전";
        }
        if (seconds < HOUR) {
            return seconds / MINUTE + "분 전";
        }
        if (seconds < DAY) {
            return seconds / HOUR + "시간 전";
        }
        // 달과 해는 길이가 제각각이라 초로 나누면 어긋난다. 한 달을 30일로 잡으면 1년에서
        // 하루 빠진 364일이 "12달 전" 이 된다. 여기서부터는 달력으로 센다.
        long months = ChronoUnit.MONTHS.between(time, now);
        if (months < 1) {
            return seconds / DAY + "일 전";
        }
        long years = ChronoUnit.YEARS.between(time, now);
        if (years >= 1) {
            return years + "년 전";
        }
        return months + "달 전";
    }
}
