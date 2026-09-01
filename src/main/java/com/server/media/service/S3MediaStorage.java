package com.server.media.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.media.config.MediaProperties;
import java.io.InputStream;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
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

    @Override
    public void delete(String url) {
        String key = keyOf(url);
        if (key == null) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(key)
                    .build());
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    @Override
    public List<StoredObject> listAll() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(properties.s3().bucket())
                .prefix(properties.s3().keyPrefix() + "/")
                .build();
        try {
            // 페이지 단위로 나눠 받는다. 한 번에 1000건까지만 오므로 직접 이어 붙이지 않는다.
            return s3Client.listObjectsV2Paginator(request).contents().stream()
                    .map(object -> new StoredObject(publicUrl(object.key()), object.lastModified()))
                    .toList();
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE, exception);
        }
    }

    /** @return 우리 버킷의 주소면 객체 키, 아니면 {@code null} */
    private String keyOf(String url) {
        if (url == null) {
            return null;
        }
        String prefix = publicUrl("");
        if (!url.startsWith(prefix)) {
            return null;
        }
        String key = url.substring(prefix.length());
        return key.isBlank() ? null : key;
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
