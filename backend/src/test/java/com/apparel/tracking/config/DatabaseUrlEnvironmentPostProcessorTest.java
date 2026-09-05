package com.apparel.tracking.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * The URI forms managed platforms actually hand out. Each of these reached the
 * datasource verbatim at some point and failed with {@code 'url' must start with "jdbc"}.
 */
class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    private Map<String, Object> translate(Map<String, String> env) {
        MockEnvironment environment = new MockEnvironment();
        env.forEach(environment::setProperty);

        processor.postProcessEnvironment(environment, null);

        return Map.of(
                "url", String.valueOf(environment.getProperty("spring.datasource.url")),
                "username", String.valueOf(environment.getProperty("spring.datasource.username")),
                "password", String.valueOf(environment.getProperty("spring.datasource.password")));
    }

    @Test
    void convertsUriStyleUrlAndSplitsOutCredentials() {
        var result = translate(Map.of("DB_URL", "postgresql://carol:s3cret@db.internal:5432/apparel"));

        assertThat(result.get("url")).isEqualTo("jdbc:postgresql://db.internal:5432/apparel");
        assertThat(result.get("username")).isEqualTo("carol");
        assertThat(result.get("password")).isEqualTo("s3cret");
    }

    @Test
    void acceptsTheHerokuStylePostgresScheme() {
        var result = translate(Map.of("DB_URL", "postgres://carol:s3cret@db.internal:5432/apparel"));

        assertThat(result.get("url")).isEqualTo("jdbc:postgresql://db.internal:5432/apparel");
    }

    @Test
    void keepsQueryParametersSoSslModeSurvives() {
        var result = translate(
                Map.of("DB_URL", "postgresql://carol:s3cret@db.neon.tech/apparel?sslmode=require"));

        assertThat(result.get("url")).isEqualTo("jdbc:postgresql://db.neon.tech/apparel?sslmode=require");
    }

    @Test
    void decodesAPercentEncodedPassword() {
        // A generated password containing @ / : must arrive encoded, or the URI is ambiguous.
        var result = translate(Map.of("DB_URL", "postgresql://carol:p%40ss%3Aword@db.internal:5432/apparel"));

        assertThat(result.get("password")).isEqualTo("p@ss:word");
    }

    @Test
    void leavesAnExplicitJdbcUrlAlone() {
        var result = translate(Map.of("DB_URL", "jdbc:postgresql://localhost:5432/apparel"));

        // Nothing translated, so the placeholder in application.yml still applies.
        assertThat(result.get("url")).isEqualTo("null");
    }

    @Test
    void separateCredentialsWinOverThoseEmbeddedInTheUri() {
        var result = translate(Map.of(
                "DB_URL", "postgresql://stale:stale@db.internal:5432/apparel",
                "DB_USERNAME", "carol",
                "DB_PASSWORD", "s3cret"));

        assertThat(result.get("url")).isEqualTo("jdbc:postgresql://db.internal:5432/apparel");
        assertThat(result.get("username")).isEqualTo("null");
        assertThat(result.get("password")).isEqualTo("null");
    }

    @Test
    void readsDatabaseUrlWhenDbUrlIsAbsent() {
        var result = translate(Map.of("DATABASE_URL", "postgresql://carol:s3cret@db.internal:5432/apparel"));

        assertThat(result.get("url")).isEqualTo("jdbc:postgresql://db.internal:5432/apparel");
    }

    @Test
    void ignoresSomethingThatIsNotAUrlRatherThanGuessing() {
        var result = translate(Map.of("DB_URL", "postgresql://"));

        assertThat(result.get("url")).isEqualTo("null");
    }
}
