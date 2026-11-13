package ru.mirea.library.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;

import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Библиотечная система").version("1.0"))
                .schemaRequirement(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
