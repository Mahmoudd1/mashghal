package com.apparel.tracking.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application-owned configuration, bound from the {@code app.*} prefix.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Jwt jwt, Bootstrap bootstrap, Seed seed) {

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }

    public record Jwt(
            String secret,
            @DefaultValue("apparel-tracking") String issuer,
            @DefaultValue("PT12H") Duration accessTokenTtl) {
    }

    /** Credentials for the single admin user created on first start-up. */
    public record Bootstrap(
            @DefaultValue("admin") String adminUsername,
            @DefaultValue("admin123") String adminPassword) {
    }

    /** Development conveniences. Never enable demo data outside development. */
    public record Seed(@DefaultValue("false") boolean demoData) {
    }
}
