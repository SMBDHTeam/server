package com.server.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record ShareLinkCreateRequest(
        @Schema(description = "공유 링크 유효기간(일)", example = "7")
        @Positive @Max(365) Integer expiresInDays
) {
}
