package com.apparel.tracking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI apparelTrackingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Apparel Manufacturing Tracking API")
                .version("v1")
                .description("Fabric inventory, cutting, production pipeline and sales tracking."));
    }
}
