package com.server.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PlaceResolveRequest(
        @NotBlank String source,
        @NotBlank String externalId,
        @NotBlank String name,
        String category,
        String address,
        @NotNull BigDecimal longitude,
        @NotNull BigDecimal latitude,
        String placeUrl
) {
}
