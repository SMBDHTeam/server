package com.server.media.service;

import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.common.error.FieldViolation;
import com.server.media.config.MediaProperties;
import com.server.media.domain.MediaFormat;
import com.server.media.dto.MediaUploadListResponse;
import com.server.media.dto.MediaUploadResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    /** 한 폴더에 파일이 무한정 쌓이지 않게 연·월로 나눈다. */
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    private final ObjectProvider<MediaStorage> storageProvider;
    private final MediaProperties properties;

    public MediaService(ObjectProvider<MediaStorage> storageProvider, MediaProperties properties) {
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    /**
     * 보낸 순서를 지켜 업로드한다.
     *
     * <p>중간에 하나라도 규칙에 어긋나면 아무것도 올리지 않는다. 절반만 올라가면 프론트가
     * 어디까지 성공했는지 알 수 없고, 버려진 파일이 버킷에 남기 때문이다.
     */
    public MediaUploadListResponse upload(Long userId, List<MultipartFile> files) {
        MediaStorage storage = requireStorage();
        List<MediaFormat> formats = validate(files);

        List<MediaUploadResponse> uploaded = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            MediaFormat format = formats.get(index);
            try (InputStream content = file.getInputStream()) {
                String url = storage.upload(key(format), format.getContentType(), content, file.getSize());
                uploaded.add(new MediaUploadResponse(url, format.getMediaType()));
            } catch (IOException exception) {
                throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE, exception);
            }
        }
        // 버킷에 남은 파일을 올린 사람과 잇는 유일한 단서다. 게시물에 붙지 않은 파일도 생긴다.
        log.info("미디어 업로드 완료 userId={} count={}", userId, uploaded.size());
        return new MediaUploadListResponse(List.copyOf(uploaded));
    }

    /** 업로드가 꺼진 환경에서도 서버는 떠야 한다. 나머지 위임 계층과 같이 503 으로 알린다. */
    private MediaStorage requireStorage() {
        MediaStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        return storage;
    }

    /** 한 건이라도 올리기 전에 전부 확인한다. 반환값은 파일 순서에 맞춘 형식 목록이다. */
    private List<MediaFormat> validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE,
                    List.of(FieldViolation.of("files", "파일을 한 건 이상 보내야 합니다.")));
        }
        if (files.size() > properties.maxFileCount()) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE,
                    List.of(FieldViolation.of("files",
                            "한 번에 %d건까지 올릴 수 있습니다.".formatted(properties.maxFileCount()))));
        }
        return files.stream().map(this::resolveFormat).toList();
    }

    private MediaFormat resolveFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE,
                    List.of(FieldViolation.of("files", "빈 파일은 올릴 수 없습니다.")));
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_TOO_LARGE,
                    List.of(FieldViolation.of("files",
                            "한 건당 %dMB 까지 올릴 수 있습니다."
                                    .formatted(properties.maxFileSize().toMegabytes()))));
        }
        MediaFormat format = MediaFormat.byExtension(extensionOf(file.getOriginalFilename()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_FORMAT));
        if (!format.matches(head(file))) {
            // 확장자만 바꾼 파일이다. 형식이 다르다는 사실만 알리고 무엇이었는지는 말하지 않는다.
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_FORMAT,
                    List.of(FieldViolation.of("files", "파일 내용이 확장자와 맞지 않습니다.")));
        }
        return format;
    }

    private byte[] head(MultipartFile file) {
        try (InputStream content = file.getInputStream()) {
            return content.readNBytes(MediaFormat.signatureLength());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE, exception);
        }
    }

    /**
     * 올린 파일 이름은 쓰지 않는다. 같은 이름이 서로를 덮어쓰고, 경로 문자나 한글이 섞이면
     * URL 이 깨지기 때문이다.
     */
    private String key(MediaFormat format) {
        return "%s/%s/%s.%s".formatted(
                properties.s3().keyPrefix(),
                LocalDate.now().format(KEY_DATE),
                UUID.randomUUID(),
                format.getExtension());
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? null : filename.substring(dot + 1);
    }
}
