package com.server.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.media.config.MediaProperties;
import com.server.media.dto.MediaUploadResponse;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * 업로드가 목록용 사본을 함께 올리는지 고정한다. 사본 주소는 원본에서 규칙으로 얻을 수
 * 있어야 하며, 만들지 못하는 형식에서도 업로드 자체는 성공해야 한다.
 */
@DisplayName("업로드 시 축소본")
class MediaThumbnailUploadTest {

    private static final long USER_ID = 1L;

    private final RecordingStorage storage = new RecordingStorage();
    private final MediaService mediaService =
            new MediaService(providerOf(storage), properties(), new ThumbnailGenerator());

    @Test
    @DisplayName("사진은 원본 옆에 _thumb.jpg 로 사본을 올리고 주소를 함께 준다")
    void uploadsThumbnailNextToOriginal() {
        MediaUploadResponse uploaded = mediaService
                .upload(USER_ID, List.of(new MockMultipartFile("files", "photo.png", "image/png", png(1200, 800))))
                .mediaList()
                .get(0);

        assertThat(uploaded.url()).endsWith(".png");
        assertThat(uploaded.thumbnailUrl()).isNotNull();
        assertThat(uploaded.thumbnailUrl())
                .isEqualTo(uploaded.url().replace(".png", "_thumb.jpg"));
        assertThat(storage.keys).hasSize(2);
        assertThat(storage.contentTypes).containsExactly("image/png", "image/jpeg");
    }

    @Test
    @DisplayName("사본을 만들지 못하는 형식은 원본만 올리고 주소는 null 이다")
    void skipsThumbnailForUnreadableFormat() {
        // webp 는 ImageIO 가 읽지 못한다. 업로드는 성공하고 화면이 원본을 쓴다.
        byte[] webp = new byte[] {
                0x52, 0x49, 0x46, 0x46, 0x10, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20};

        MediaUploadResponse uploaded = mediaService
                .upload(USER_ID, List.of(new MockMultipartFile("files", "photo.webp", "image/webp", webp)))
                .mediaList()
                .get(0);

        assertThat(uploaded.thumbnailUrl()).isNull();
        assertThat(storage.keys).hasSize(1);
    }

    @Test
    @DisplayName("객체 키 규칙: 확장자만 바꾼 자리에 사본을 둔다")
    void thumbnailKeyRule() {
        assertThat(MediaService.thumbnailKey("posts/2026/09/abc.png")).isEqualTo("posts/2026/09/abc_thumb.jpg");
        assertThat(MediaService.thumbnailKey("posts/2026/09/abc")).isEqualTo("posts/2026/09/abc_thumb.jpg");
    }

    private static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }

    private static MediaProperties properties() {
        return new MediaProperties(
                DataSize.ofMegabytes(10),
                10,
                new MediaProperties.S3(true, "bucket", "ap-northeast-2", "posts"),
                new MediaProperties.OrphanCleanup(false, Duration.ofHours(24)));
    }

    private static ObjectProvider<MediaStorage> providerOf(MediaStorage storage) {
        return new ObjectProvider<>() {
            @Override
            public MediaStorage getObject() {
                return storage;
            }

            @Override
            public MediaStorage getObject(Object... args) {
                return storage;
            }

            @Override
            public MediaStorage getIfAvailable() {
                return storage;
            }

            @Override
            public MediaStorage getIfUnique() {
                return storage;
            }
        };
    }

    /** 올린 키와 Content-Type 만 기록한다. S3 는 부르지 않는다. */
    private static final class RecordingStorage implements MediaStorage {

        private final List<String> keys = new ArrayList<>();
        private final List<String> contentTypes = new ArrayList<>();

        @Override
        public String upload(String key, String contentType, InputStream content, long size) {
            keys.add(key);
            contentTypes.add(contentType);
            return "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        }

        @Override
        public void delete(String url) {
        }

        @Override
        public void forEachPage(Consumer<List<StoredObject>> pageConsumer) {
        }
    }
}
