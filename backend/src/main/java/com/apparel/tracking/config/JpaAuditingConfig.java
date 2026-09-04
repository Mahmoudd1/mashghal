package com.apparel.tracking.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Populates {@code created_by} / {@code updated_by} on every entity write.
 * Falls back to {@code system} for migrations, seeders and scheduled work.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    public static final String SYSTEM_USER = "system";

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(currentUsername());
    }

    /** Unauthenticated and anonymous requests are both attributed to {@code system}. */
    public static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM_USER;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? SYSTEM_USER : name;
    }
}
