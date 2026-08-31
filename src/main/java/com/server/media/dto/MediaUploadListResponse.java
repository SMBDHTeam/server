package com.server.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * @param mediaList 보낸 순서 그대로다. 프론트가 정렬을 다시 맞출 필요가 없다
 */
@Schema(description = "업로드 결과")
public record MediaUploadListResponse(List<MediaUploadResponse> mediaList) {
}
