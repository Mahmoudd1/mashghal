package com.apparel.tracking.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built Angular app and makes its client-side routes deep-linkable.
 *
 * <p>The browser asking for {@code /cuts} must get {@code index.html}, not a 404 —
 * the router resolves the path once the app has booted. Anything that looks like a
 * real file is served as itself, and {@code /api} is left alone entirely so a
 * mistyped endpoint still returns a JSON 404 rather than a page of HTML.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // API and actuator paths are handled by controllers; never
                        // hand them the SPA shell.
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
