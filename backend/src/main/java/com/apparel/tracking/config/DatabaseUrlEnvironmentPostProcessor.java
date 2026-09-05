package com.apparel.tracking.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Accepts a URI-style database URL, the form managed platforms hand out.
 *
 * <p>Railway, Render, Heroku, Neon and Supabase all publish a connection string
 * shaped like {@code postgresql://user:password@host:5432/dbname}. JDBC will not
 * take it — HikariCP rejects anything not starting with {@code jdbc:} — so
 * pasting the platform's own variable into {@code DB_URL} fails at start-up with
 * {@code 'url' must start with "jdbc"}. Splitting it by hand works until the
 * credentials rotate, at which point it fails again.
 *
 * <p>This translates the URI into the three properties the app already binds,
 * so either form works. An explicit {@code jdbc:} URL is passed through
 * untouched, and credentials given separately always win over any embedded in
 * the URI — so a deliberate override is never silently replaced.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Platform-provided variables, in the order we prefer them. */
    private static final String[] SOURCE_KEYS = {"DB_URL", "DATABASE_URL", "POSTGRES_URL"};

    private static final String PROPERTY_SOURCE_NAME = "databaseUrlTranslation";

    @Override
    public int getOrder() {
        // After config files are loaded, before anything binds a DataSource.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String key : SOURCE_KEYS) {
            String value = environment.getProperty(key);
            if (!StringUtils.hasText(value) || !isUriStyle(value)) {
                continue;
            }
            Map<String, Object> translated = translate(value, environment);
            if (!translated.isEmpty()) {
                environment.getPropertySources()
                        .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, translated));
            }
            return;
        }
    }

    private boolean isUriStyle(String value) {
        return value.startsWith("postgres://") || value.startsWith("postgresql://");
    }

    private Map<String, Object> translate(String value, ConfigurableEnvironment environment) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception ex) {
            // Not parseable: leave it alone and let the datasource report the real problem.
            return Map.of();
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            return Map.of();
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host);
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        // A URI with no path still needs one; the driver requires a database name.
        String path = uri.getPath();
        jdbc.append(StringUtils.hasText(path) ? path : "/");
        if (StringUtils.hasText(uri.getQuery())) {
            jdbc.append('?').append(uri.getQuery());
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbc.toString());

        // Credentials set explicitly are a deliberate choice; never overwrite them.
        String[] credentials = splitUserInfo(uri.getRawUserInfo());
        if (credentials != null) {
            if (!StringUtils.hasText(environment.getProperty("DB_USERNAME"))) {
                properties.put("spring.datasource.username", credentials[0]);
            }
            if (!StringUtils.hasText(environment.getProperty("DB_PASSWORD"))) {
                properties.put("spring.datasource.password", credentials[1]);
            }
        }
        return properties;
    }

    /** Returns {user, password}, or null when the URI carries no credentials. */
    private String[] splitUserInfo(String rawUserInfo) {
        if (!StringUtils.hasText(rawUserInfo)) {
            return null;
        }
        int separator = rawUserInfo.indexOf(':');
        String user = separator < 0 ? rawUserInfo : rawUserInfo.substring(0, separator);
        String password = separator < 0 ? "" : rawUserInfo.substring(separator + 1);
        return new String[] {decode(user), decode(password)};
    }

    /** Passwords with reserved characters arrive percent-encoded. */
    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
