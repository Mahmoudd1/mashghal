package com.apparel.tracking.config;

import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to run in production on development credentials.
 *
 * <p>The development defaults are published in this repository, so a deployment
 * that quietly inherited one would be readable by anyone who can see the source.
 * Failing at start-up is noisy; the alternative is a system that looks fine and
 * is not.
 *
 * <p>Runs during context refresh rather than on application-ready, so the check
 * fails before the web server binds a port. A misconfigured deployment never
 * serves a single request.
 */
@Component
@Profile("prod")
public class ProductionSecretsCheck {

    /** The value committed in application.yml for local work. */
    private static final String DEV_SECRET_MARKER = "local-development-only";
    private static final String DEV_ADMIN_PASSWORD = "admin123";
    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties properties;

    public ProductionSecretsCheck(AppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        String secret = properties.jwt().secret();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("APP_JWT_SECRET must be set in production");
        }
        if (secret.contains(DEV_SECRET_MARKER)) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET is still the development value from application.yml. "
                            + "Generate one, for example: openssl rand -base64 48");
        }
        // HS256 signing keys shorter than the hash are a weakness, not a preference.
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be at least %d bytes for HS256".formatted(MIN_SECRET_BYTES));
        }
        if (DEV_ADMIN_PASSWORD.equals(properties.bootstrap().adminPassword())) {
            throw new IllegalStateException(
                    "APP_ADMIN_PASSWORD is still the development default; set a real one");
        }
    }
}
