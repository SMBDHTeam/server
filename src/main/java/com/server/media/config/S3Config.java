package com.server.media.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 자격 증명은 지정하지 않는다. SDK 기본 탐색 순서가 환경변수
 * ({@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY}) 를 먼저 보기 때문이다. 키를
 * 설정 파일에 두지 않는 것은 yaml 이 Git 에 올라가는 파일이라서다.
 *
 * <p>{@code app.media.s3.enabled} 가 꺼져 있으면 이 설정 자체가 만들어지지 않는다. 키가 없는
 * 환경에서도 서버가 뜨게 하려는 것이고, 그때 업로드는 503 으로 응답한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.media.s3", name = "enabled", havingValue = "true")
public class S3Config {

    @Bean
    public S3Client s3Client(MediaProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.s3().region()))
                .build();
    }
}
