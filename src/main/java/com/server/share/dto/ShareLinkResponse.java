package com.server.share.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShareLinkResponse(
        UUID id,
        String token,
        String url,
        OffsetDateTime expiresAt
) {
}
