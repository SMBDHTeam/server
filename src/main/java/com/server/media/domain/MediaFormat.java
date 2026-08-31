package com.server.media.domain;

import com.server.post.domain.MediaType;
import java.util.Arrays;
import java.util.Optional;

/**
 * 업로드를 허용하는 파일 형식이다.
 *
 * <p>확장자와 Content-Type 은 모두 클라이언트가 정하는 값이라 그대로 믿을 수 없다. 그래서
 * 파일 앞머리의 고정 바이트(시그니처)를 함께 본다. 실행 파일 이름만 {@code .jpg} 로 바꿔
 * 올리는 것을 막기 위해서다.
 */
public enum MediaFormat {

    JPEG(MediaType.IMAGE, "jpg", "image/jpeg", new int[] {0xFF, 0xD8, 0xFF}),
    PNG(MediaType.IMAGE, "png", "image/png", new int[] {0x89, 0x50, 0x4E, 0x47}),
    GIF(MediaType.IMAGE, "gif", "image/gif", new int[] {0x47, 0x49, 0x46, 0x38}),
    /** RIFF....WEBP. 5~8번째 바이트가 파일 크기라 앞 네 글자만 본다. */
    WEBP(MediaType.IMAGE, "webp", "image/webp", new int[] {0x52, 0x49, 0x46, 0x46}),
    /** ISO base media. 시그니처가 4번째 바이트부터라 offset 을 둔다. */
    MP4(MediaType.VIDEO, "mp4", "video/mp4", 4, new int[] {0x66, 0x74, 0x79, 0x70}),
    MOV(MediaType.VIDEO, "mov", "video/quicktime", 4, new int[] {0x66, 0x74, 0x79, 0x70});

    private final MediaType mediaType;
    private final String extension;
    private final String contentType;
    private final int signatureOffset;
    private final int[] signature;

    MediaFormat(MediaType mediaType, String extension, String contentType, int[] signature) {
        this(mediaType, extension, contentType, 0, signature);
    }

    MediaFormat(
            MediaType mediaType,
            String extension,
            String contentType,
            int signatureOffset,
            int[] signature
    ) {
        this.mediaType = mediaType;
        this.extension = extension;
        this.contentType = contentType;
        this.signatureOffset = signatureOffset;
        this.signature = signature;
    }

    /** 확장자로 형식을 찾는다. {@code jpeg} 처럼 흔한 변형도 받는다. */
    public static Optional<MediaFormat> byExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.trim().toLowerCase();
        if ("jpeg".equals(normalized)) {
            normalized = "jpg";
        }
        String target = normalized;
        return Arrays.stream(values()).filter(format -> format.extension.equals(target)).findFirst();
    }

    /** 파일 앞머리가 이 형식의 시그니처와 맞는지 본다. */
    public boolean matches(byte[] head) {
        if (head == null || head.length < signatureOffset + signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((head[signatureOffset + index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    /** 시그니처를 확인하려면 앞에서 몇 바이트를 읽어야 하는지. */
    public static int signatureLength() {
        return Arrays.stream(values())
                .mapToInt(format -> format.signatureOffset + format.signature.length)
                .max()
                .orElse(0);
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }
}
