package com.apparel.tracking.auth.dto;

import com.apparel.tracking.auth.domain.AppUser;
import com.apparel.tracking.auth.domain.UserRole;

public record AuthenticatedUserDto(Long id, String username, String displayName, UserRole role) {

    public static AuthenticatedUserDto from(AppUser user) {
        return new AuthenticatedUserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
