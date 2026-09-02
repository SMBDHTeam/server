package com.server.media.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 커뮤니티 미디어 업로드 설정.
 *
 * @param maxFileSize   한 건당 허용 크기
 * @param maxFileCount  한 요청에 올릴 수 있는 건수. 게시물 첨부 상한과 맞춘다
 * @param s3            저장소 설정
 * @param orphanCleanup 게시물에 붙지 않은 파일 정리
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
        DataSize maxFileSize,
        int maxFileCount,
        S3 s3,
        OrphanCleanup orphanCleanup
) {

    /**
     * @param enabled   꺼두면 업로드가 503 을 반환한다. 키가 없는 환경에서 서버가 뜨게 한다
     * @param bucket    버킷 이름
     * @param region    버킷 리전
     * @param keyPrefix 객체 키 앞에 붙이는 경로. 다른 용도와 섞이지 않게 한다
     */
    public record S3(boolean enabled, String bucket, String region, String keyPrefix) {
    }

    /**
     * @param enabled 꺼두면 정리하지 않는다. 저장소 목록 조회 권한이 없는 환경을 위한 것이다
     * @param minAge  이 시간이 지나지 않은 파일은 건드리지 않는다. 아직 글을 쓰는 중일 수 있다
     */
    public record OrphanCleanup(boolean enabled, Duration minAge) {

        /** 설정에서 빠졌을 때 쓰는 값. 업로드부터 게시물 작성까지 하루가 걸리는 일은 없다. */
        private static final Duration DEFAULT_MIN_AGE = Duration.ofHours(24);

        public OrphanCleanup {
            // 값이 비면 기준 시각이 없어 정리 도중 터진다. 빈 값을 "제한 없음" 으로 읽어
            // 방금 올라온 파일까지 지우는 것보다, 안전한 쪽으로 채워 두는 편이 낫다.
            minAge = minAge == null ? DEFAULT_MIN_AGE : minAge;
        }
    }
}
