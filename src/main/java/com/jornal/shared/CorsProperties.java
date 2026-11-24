package com.jornal.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Orígenes del frontend con permiso para llamar a la API. Acepta patrones como {@code https://*.jornal.localhost}. */
@ConfigurationProperties("jornal.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
