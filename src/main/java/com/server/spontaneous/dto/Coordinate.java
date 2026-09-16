package com.server.spontaneous.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record Coordinate(
        @NotNull(message = "위도를 입력해 주세요.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,
        @NotNull(message = "경도를 입력해 주세요.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude,
        String name,
        String address
) {
    public Coordinate(Double latitude, Double longitude) {
        this(latitude, longitude, null, null);
    }
}
