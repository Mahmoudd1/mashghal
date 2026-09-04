package com.apparel.tracking.auth.dto;

import com.apparel.tracking.auth.domain.UserRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code password} is required when creating; leave it null to keep the existing one. */
public record UserRequest(
        @NotBlank @Size(max = 64) String username,
        @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 128) String displayName,
        @NotNull UserRole role,
        Boolean enabled) {
}
