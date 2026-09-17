package com.server.media.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 목록에 쓸 작은 사본을 만든다.
 *
 * <p>휴대폰으로 찍은 사진은 한 장에 수백 KB~수 MB 다. 커뮤니티 목록은 한 번에 스무 건을
 * 불러오므로 원본을 그대로 쓰면 첫 화면에서 10MB 가까이 내려받는다. 목록에 실제로 그려지는
 * 크기는 한 변 200px 안팎이라 그만한 화질이 필요 없다.
 *
 * <p>JPEG 로 저장한다. 투명한 PNG 는 흰 바탕에 얹는다. 목록 카드의 배경이 흰색이라
 * 검게 나오는 것보다 낫다. 원본은 지우지 않으므로 상세와 확대 보기는 그대로다.
 *
 * <p><b>실패해도 업로드를 막지 않는다.</b> ImageIO 가 읽지 못하는 형식(webp)과 영상은
 * 빈 값을 주고, 그 경우 화면은 원본을 쓴다.
 */
@Component
public class ThumbnailGenerator {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailGenerator.class);

    /**
     * 긴 변 기준 목표 크기. 목록 카드가 가장 큰 화면에서도 한 변 400px 을 넘지 않고,
     * 고해상도 화면을 감안해 그 1.2배를 둔다.
     */
    static final int MAX_EDGE = 480;

    /** JPEG 품질. 0.8 아래로 내리면 사진에 얼룩이 보이기 시작한다. */
    private static final float QUALITY = 0.8f;

    static final String CONTENT_TYPE = "image/jpeg";
    static final String EXTENSION = "jpg";

    /**
     * @return 축소한 JPEG. 읽을 수 없는 형식이거나 이미 충분히 작으면 빈 값
     */
    public Optional<byte[]> generate(byte[] source) {
        if (source == null || source.length == 0) {
            return Optional.empty();
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
            if (original == null) {
                return Optional.empty();
            }
            int longestEdge = Math.max(original.getWidth(), original.getHeight());
            if (longestEdge <= MAX_EDGE) {
                // 이미 작다. 사본을 만들어 봐야 용량이 줄지 않고 객체만 하나 더 생긴다.
                return Optional.empty();
            }
            return Optional.of(encode(resize(original, (double) MAX_EDGE / longestEdge)));
        } catch (IOException | RuntimeException exception) {
            // OutOfMemoryError 가 아닌 한 업로드는 계속되어야 한다. 원본만 쓰면 그만이다.
            log.warn("Thumbnail generation failed. Falling back to the original image.", exception);
            return Optional.empty();
        }
    }

    private static BufferedImage resize(BufferedImage original, double ratio) {
        int width = Math.max(1, (int) Math.round(original.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(original.getHeight() * ratio));

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // JPEG 에는 투명도가 없다. 먼저 흰색으로 덮지 않으면 투명한 부분이 검게 남는다.
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(EXTENSION);
        if (!writers.hasNext()) {
            throw new IOException("JPEG writer not available");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream target = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(target);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
