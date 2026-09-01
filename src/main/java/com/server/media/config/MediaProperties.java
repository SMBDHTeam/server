package com.server.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 커뮤니티 미디어 업로드 설정.
 *
 * @param maxFileSize  한 건당 허용 크기
 * @param maxFileCount 한 요청에 올릴 수 있는 건수. 게시물 첨부 상한과 맞춘다
 * @param s3           저장소 설정
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(DataSize maxFileSize, int maxFileCount, S3 s3) {

    /**
     * @param enabled   꺼두면 업로드가 503 을 반환한다. 키가 없는 환경에서 서버가 뜨게 한다
     * @param bucket    버킷 이름
     * @param region    버킷 리전
     * @param keyPrefix 객체 키 앞에 붙이는 경로. 다른 용도와 섞이지 않게 한다
     */
    public record S3(boolean enabled, String bucket, String region, String keyPrefix) {
    }
}
