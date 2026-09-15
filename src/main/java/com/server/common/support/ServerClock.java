package com.server.common.support;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 서버가 쓰는 시계. 언제나 한국 시각이다.
 *
 * <p>{@code LocalDateTime.now()} 는 시간대를 묻지 않고 도는 컴퓨터의 시계를 그대로 읽는다.
 * 배포 이미지에 {@code TZ=Asia/Seoul} 을 박아 두어 지금은 한국 시각이 나오지만, 그 설정에
 * 기대는 동안에는 실행 환경이 바뀌면 저장되는 값도 함께 바뀐다.
 *
 * <p>실제로 그 일이 있었다. 관리자 통계는 {@code LocalDate.now(KOREA_ZONE)} 으로 날짜를
 * 자르는데 데이터는 서버 시계로 저장돼서, UTC 로 도는 GitHub Actions 에서만 한국 시각
 * 0~9시에 하루가 어긋나 테스트가 깨졌다. 읽는 쪽은 한국으로 고정하고 쓰는 쪽은 고정하지
 * 않았기 때문이다.
 *
 * <p>여기서 시간대를 정하면 어느 환경에서 돌든 같은 값이 저장된다.
 *
 * <p><b>아직 옮기지 못한 곳이 있다.</b> {@code Report} 의 접수 시각 한 곳이 {@code LocalDateTime.now()}
 * 를 그대로 쓴다. 배포 이미지가 한국 시각이라 지금은 같은 값이 나온다.
 */
public final class ServerClock {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private ServerClock() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(KOREA);
    }
}
