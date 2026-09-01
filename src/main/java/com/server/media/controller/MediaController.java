package com.server.media.controller;

import com.server.media.dto.MediaUploadListResponse;
import com.server.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "커뮤니티 미디어", description = "게시물에 붙일 사진·영상 업로드")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "사진·영상 업로드",
            description = "게시물 작성 전에 파일을 먼저 올린다. 응답의 url 과 mediaType 을 "
                    + "POST /api/v1/posts 의 mediaList 에 그대로 넣는다. "
                    + "확장자와 파일 내용이 함께 확인되며, 하나라도 어긋나면 전부 거부한다."
    )
    public MediaUploadListResponse upload(
            // TODO: 인증 도입 시 제거하고 인증 주체에서 사용자 ID 를 받는다. 임시 식별 수단이다.
            //       지금은 업로드 기록을 남기는 데만 쓴다.
            @Parameter(description = "업로더 ID. 인증 도입 전까지 쓰는 임시 헤더다.", example = "1")
            @RequestHeader("X-User-Id") Long userId,

            @Parameter(description = "올릴 파일. 여러 건을 같은 이름으로 반복해 보낸다.")
            @RequestPart("files") List<MultipartFile> files
    ) {
        return mediaService.upload(userId, files);
    }
}
