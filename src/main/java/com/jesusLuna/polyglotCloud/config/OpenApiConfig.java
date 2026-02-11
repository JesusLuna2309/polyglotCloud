package com.jesusLuna.polyglotCloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PolyglotCloud API")
                        .description("API REST para gestión de snippets de código con seguridad post-cuántica")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Jesús Luna")
                                .email("support@polyglotcloud.com")
                                .url("https://github.com/JesusLuna2309/polyglotCloud"))
                        .license(new License()
                                .name("Proprietary License")
                                .url("https://github.com/JesusLuna2309/polyglotCloud/blob/main/LICENSE")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Ingresa tu JWT token aquí")));
        }
}
