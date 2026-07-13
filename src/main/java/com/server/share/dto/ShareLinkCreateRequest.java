package com.server.share.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record ShareLinkCreateRequest(
        @Positive @Max(365) Integer expiresInDays
) {
}
