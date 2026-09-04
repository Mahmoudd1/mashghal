package com.apparel.tracking.common.security;

import com.apparel.tracking.auth.domain.UserRole;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether the caller may see money.
 *
 * <p>Prices are stripped server-side rather than hidden in the UI: a column the
 * browser never receives cannot be read out of a network tab, and the same rule
 * then holds for every client — the REST API, Swagger, a future mobile app.
 */
@Component
public class PricePolicy {

    /** Only the owner sees purchase prices, costs, and anything derived from them. */
    public boolean canSeePrices() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(UserRole.OWNER.authority()::equals);
    }
}
