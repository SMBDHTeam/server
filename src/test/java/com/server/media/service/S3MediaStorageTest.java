package com.server.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.media.config.MediaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/**
 * 삭제는 URL 에서 객체 키를 되짚어야 한다. 우리 버킷이 아닌 주소까지 지우려 들면 안 되는데,
 * 인증 도입 전에 만든 게시물에는 외부 URL 과 {@code blob:} 주소가 남아 있다.
 */
@DisplayName("S3 저장소")
class S3MediaStorageTest {

    private static final String BUCKET = "test-bucket";
    private static final String BASE = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";

    private final S3Client s3Client = Mockito.mock(S3Client.class);
    private final S3MediaStorage storage = new S3MediaStorage(s3Client, properties());

    @Test
    @DisplayName("우리 버킷 주소면 객체 키만 뽑아 지운다")
    void deletesByKey() {
        storage.delete(BASE + "posts/2026/09/a1b2.jpg");

        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo("posts/2026/09/a1b2.jpg");
    }

    @Test
    @DisplayName("http 로 적힌 우리 버킷 주소도 알아본다")
    void deletesHttpVariant() {
        // 우리가 돌려주는 주소는 https 지만 손으로 적은 http 가 게시물에 들어올 수 있다.
        // 남의 주소로 보면 게시물을 지워도 파일이 남는다.
        storage.delete("http://test-bucket.s3.ap-northeast-2.amazonaws.com/posts/a.jpg");

        ArgumentCaptor<DeleteObjectRequest> request =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("posts/a.jpg");
    }

    @Test
    @DisplayName("우리 버킷이 아닌 주소는 건드리지 않는다")
    void ignoresForeignUrl() {
        storage.delete("https://example.com/media/1.jpg");
        storage.delete("blob:http://localhost:3000/fa3b7e69");
        storage.delete(null);

        verify(s3Client, never()).deleteObject((DeleteObjectRequest) any());
    }

    @Test
    @DisplayName("저장소가 응답하지 않으면 503 으로 알린다")
    void mapsStorageFailure() {
        Mockito.doThrow(SdkClientException.create("연결 실패"))
                .when(s3Client).deleteObject((DeleteObjectRequest) any());

        assertThatThrownBy(() -> storage.delete(BASE + "posts/a.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    private static MediaProperties properties() {
        return new MediaProperties(
                DataSize.ofMegabytes(10),
                10,
                new MediaProperties.S3(true, BUCKET, "ap-northeast-2", "posts"),
                new MediaProperties.OrphanCleanup(false, java.time.Duration.ofHours(24)));
    }
}
