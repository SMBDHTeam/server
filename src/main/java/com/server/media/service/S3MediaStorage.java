package com.server.media.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.media.config.MediaProperties;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.exception.SdkException;

@Component
@ConditionalOnBean(S3Client.class)
public class S3MediaStorage implements MediaStorage {

    private final S3Client s3Client;
    private final MediaProperties properties;

    public S3MediaStorage(S3Client s3Client, MediaProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public String upload(String key, String contentType, InputStream content, long size) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .contentType(contentType)
                // 길이를 넘기지 않으면 SDK 가 스트림을 통째로 메모리에 담는다.
                .contentLength(size)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(content, size));
        } catch (SdkException exception) {
            // S3Exception 만 잡으면 연결 실패·타임아웃이 그대로 올라가 500 이 나간다.
            // 저장소가 응답하지 않는 것은 서버 결함이 아니므로 503 으로 알린다.
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
        return publicUrl(key);
    }

    /**
     * 버킷을 공개 읽기로 열어 두었기 때문에 서명 없는 주소를 그대로 쓴다. 비공개로 바꾸면
     * 여기서 presigned URL 을 만들어야 한다.
     */
    private String publicUrl(String key) {
        return "https://%s.s3.%s.amazonaws.com/%s"
                .formatted(properties.s3().bucket(), properties.s3().region(), key);
    }
}
