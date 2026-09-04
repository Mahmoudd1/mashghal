package com.apparel.tracking.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, AuthenticatedUserDto user) {
}
