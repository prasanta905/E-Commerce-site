package com.shopsphere.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class OpenApiConfig {

    @Bean
    public OpenAPI shopsphereOpenApi() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("ShopSphere API")
                        .description("REST API for customers and admins of the ShopSphere e-commerce platform. "
                                + "Endpoints marked with lock icons require `Authorization: Bearer <accessToken>`.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme().name(schemeName).type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")));
    }
}
