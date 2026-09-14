package com.server.admin.dto;

import com.server.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
        @Schema(description = "USER 또는 ADMIN", example = "ADMIN")
        @NotNull UserRole role
) {
}
