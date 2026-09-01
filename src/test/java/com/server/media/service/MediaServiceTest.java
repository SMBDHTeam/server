package com.server.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.media.config.MediaProperties;
import com.server.media.dto.MediaUploadListResponse;
import com.server.post.domain.MediaType;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 검증 규칙을 고정한다. S3 호출은 {@link RecordingStorage} 로 대신하고, 이 테스트는
 * 무엇을 거부하는지와 거부할 때 아무것도 올리지 않는지만 본다.
 */
@DisplayName("미디어 업로드")
class MediaServiceTest {

    private static final long USER_ID = 1L;
    private static final int MAX_FILE_COUNT = 10;

    private RecordingStorage storage;
    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        storage = new RecordingStorage();
        mediaService = new MediaService(providerOf(storage), properties());
    }

    @Test
    @DisplayName("올린 순서 그대로 URL 과 종류를 준다")
    void uploadsInOrder() {
        MediaUploadListResponse response = mediaService.upload(
                USER_ID, List.of(jpeg("first.jpg"), png("second.png")));

        assertThat(response.mediaList())
                .extracting(media -> media.url().substring(media.url().lastIndexOf('.')))
                .containsExactly(".jpg", ".png");
        assertThat(response.mediaList())
                .allSatisfy(media -> assertThat(media.mediaType()).isEqualTo(MediaType.IMAGE));
    }

    @Test
    @DisplayName("정상 webp 는 통과한다")
    void acceptsWebp() {
        assertThat(mediaService.upload(USER_ID, List.of(webp("photo.webp"))).mediaList())
                .singleElement()
                .satisfies(media -> assertThat(media.url()).endsWith(".webp"));
    }

    @Test
    @DisplayName("객체 키는 연월 폴더 아래 임의 이름으로 만든다")
    void buildsDatePartitionedKey() {
        mediaService.upload(USER_ID, List.of(jpeg("사진 이름.jpg")));

        String expectedPrefix = "posts/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        // 올린 파일 이름이 키에 섞이면 덮어쓰기와 URL 깨짐이 생긴다.
        assertThat(storage.keys).singleElement().satisfies(key -> {
            assertThat(key).startsWith(expectedPrefix + "/").endsWith(".jpg");
            assertThat(key).doesNotContain("사진");
        });
    }

    @Nested
    @DisplayName("거부하는 요청")
    class Rejects {

        @Test
        @DisplayName("확장자만 이미지로 바꾼 파일")
        void contentDoesNotMatchExtension() {
            MultipartFile disguised = new MockMultipartFile(
                    "files", "evil.jpg", "image/jpeg", "MZ not an image".getBytes());

            assertThatThrownBy(() -> mediaService.upload(USER_ID, List.of(disguised)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_FORMAT);
        }

        @Test
        @DisplayName("RIFF 로 시작하지만 webp 가 아닌 파일")
        void riffButNotWebp() {
            // wav 도 RIFF 로 시작한다. 앞 네 글자만 보면 이미지로 통과한다.
            byte[] wav = new byte[16];
            System.arraycopy("RIFF".getBytes(), 0, wav, 0, 4);
            System.arraycopy("WAVE".getBytes(), 0, wav, 8, 4);
            MultipartFile disguised = new MockMultipartFile("files", "sound.webp", "image/webp", wav);

            assertThatThrownBy(() -> mediaService.upload(USER_ID, List.of(disguised)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_FORMAT);
        }

        @Test
        @DisplayName("허용하지 않는 확장자")
        void unsupportedExtension() {
            MultipartFile document = new MockMultipartFile(
                    "files", "resume.pdf", "application/pdf", "%PDF-1.7".getBytes());

            assertThatThrownBy(() -> mediaService.upload(USER_ID, List.of(document)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_FORMAT);
        }

        @Test
        @DisplayName("한도를 넘는 크기")
        void tooLarge() {
            byte[] oversized = new byte[(int) DataSize.ofMegabytes(11).toBytes()];
            System.arraycopy(jpegHeader(), 0, oversized, 0, jpegHeader().length);
            MultipartFile big = new MockMultipartFile("files", "big.jpg", "image/jpeg", oversized);

            assertThatThrownBy(() -> mediaService.upload(USER_ID, List.of(big)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.MEDIA_FILE_TOO_LARGE);
        }

        @Test
        @DisplayName("건수 초과")
        void tooMany() {
            List<MultipartFile> files = new ArrayList<>();
            for (int index = 0; index <= MAX_FILE_COUNT; index++) {
                files.add(jpeg("photo" + index + ".jpg"));
            }

            assertThatThrownBy(() -> mediaService.upload(USER_ID, files))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_MEDIA_FILE);
        }

        @Test
        @DisplayName("빈 목록")
        void empty() {
            assertThatThrownBy(() -> mediaService.upload(USER_ID, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_MEDIA_FILE);
        }
    }

    @Test
    @DisplayName("하나라도 어긋나면 나머지도 올리지 않는다")
    void rejectsWholeRequest() {
        MultipartFile good = jpeg("good.jpg");
        MultipartFile bad = new MockMultipartFile("files", "bad.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> mediaService.upload(USER_ID, List.of(good, bad)))
                .isInstanceOf(BusinessException.class);

        // 절반만 올라가면 프론트가 어디까지 성공했는지 알 수 없고 버킷에 고아 파일이 남는다.
        assertThat(storage.keys).isEmpty();
    }

    @Test
    @DisplayName("업로드가 꺼져 있으면 503 으로 알린다")
    void storageDisabled() {
        MediaService disabled = new MediaService(providerOf(null), properties());

        assertThatThrownBy(() -> disabled.upload(USER_ID, List.of(jpeg("photo.jpg"))))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
    }

    private static MediaProperties properties() {
        return new MediaProperties(
                DataSize.ofMegabytes(10),
                MAX_FILE_COUNT,
                new MediaProperties.S3(true, "test-bucket", "ap-northeast-2", "posts"),
                new MediaProperties.OrphanCleanup(false, java.time.Duration.ofHours(24)));
    }

    private static byte[] jpegHeader() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    }

    private static MultipartFile jpeg(String filename) {
        byte[] content = new byte[8];
        System.arraycopy(jpegHeader(), 0, content, 0, jpegHeader().length);
        return new MockMultipartFile("files", filename, "image/jpeg", content);
    }

    private static MultipartFile webp(String filename) {
        byte[] content = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, content, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, content, 8, 4);
        return new MockMultipartFile("files", filename, "image/webp", content);
    }

    private static MultipartFile png(String filename) {
        byte[] content = new byte[8];
        System.arraycopy(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}, 0, content, 0, 4);
        return new MockMultipartFile("files", filename, "image/png", content);
    }

    /** null 을 주면 저장소 빈이 없는 환경을 흉내 낸다. */
    private static ObjectProvider<MediaStorage> providerOf(MediaStorage storage) {
        return new ObjectProvider<>() {
            @Override
            public MediaStorage getObject() {
                return storage;
            }

            @Override
            public MediaStorage getIfAvailable() {
                return storage;
            }
        };
    }

    private static final class RecordingStorage implements MediaStorage {

        private final List<String> keys = new ArrayList<>();

        @Override
        public String upload(String key, String contentType, InputStream content, long size) {
            keys.add(key);
            return "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        }

        @Override
        public void delete(String url) {
            keys.remove(url);
        }

        @Override
        public List<StoredObject> listAll() {
            return List.of();
        }
    }
}
