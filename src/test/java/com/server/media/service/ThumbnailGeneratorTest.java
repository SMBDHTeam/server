package com.server.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("목록용 축소본")
class ThumbnailGeneratorTest {

    private final ThumbnailGenerator generator = new ThumbnailGenerator();

    @Test
    @DisplayName("긴 변을 기준 크기에 맞추고 비율을 지킨다")
    void resizesKeepingRatio() throws IOException {
        byte[] source = image(1600, 900, "png");

        byte[] thumbnail = generator.generate(source).orElseThrow();

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(result.getWidth()).isEqualTo(ThumbnailGenerator.MAX_EDGE);
        assertThat(result.getHeight()).isEqualTo(Math.round(900f * ThumbnailGenerator.MAX_EDGE / 1600f));
        assertThat(thumbnail.length).isLessThan(source.length);
    }

    @Test
    @DisplayName("세로가 긴 사진도 긴 변을 기준으로 줄인다")
    void resizesPortrait() throws IOException {
        byte[] thumbnail = generator.generate(image(900, 1600, "jpg")).orElseThrow();

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(result.getHeight()).isEqualTo(ThumbnailGenerator.MAX_EDGE);
        assertThat(result.getWidth()).isLessThan(ThumbnailGenerator.MAX_EDGE);
    }

    @Test
    @DisplayName("이미 작은 사진은 사본을 만들지 않는다")
    void skipsSmallImage() throws IOException {
        // 사본을 만들어도 용량이 줄지 않고 저장소에 객체만 하나 더 생긴다.
        assertThat(generator.generate(image(300, 200, "png"))).isEmpty();
    }

    @Test
    @DisplayName("읽을 수 없는 형식은 빈 값을 준다")
    void skipsUnreadableFormat() {
        // webp 와 영상이 여기에 해당한다. 화면은 원본을 쓴다.
        assertThat(generator.generate(new byte[] {0x52, 0x49, 0x46, 0x46, 1, 2, 3})).isEmpty();
        assertThat(generator.generate(new byte[0])).isEmpty();
        assertThat(generator.generate(null)).isEmpty();
    }

    /** @param format ImageIO 형식 이름. {@code png} 는 무손실이라 크기 비교가 안정적이다 */
    private static byte[] image(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            // 단색으로 채우면 압축이 지나치게 잘 되어 크기 비교가 뒤집힐 수 있다.
            for (int x = 0; x < width; x += 8) {
                graphics.setColor(new Color((x * 37) % 255, (x * 11) % 255, (x * 79) % 255));
                graphics.fillRect(x, 0, 8, height);
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }
}
