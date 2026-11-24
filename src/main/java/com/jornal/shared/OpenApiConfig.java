package com.jornal.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger con el botón "Authorize" para pegar el access token (Bearer). */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI jornalOpenApi(@Value("${jornal.region.code}") String region) {
        return new OpenAPI()
                .info(new Info()
                        .title("Jornal API (" + region + ")")
                        .version("v1")
                        .description("Core regional. Idioma con Accept-Language; token con POST /auth/login."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
