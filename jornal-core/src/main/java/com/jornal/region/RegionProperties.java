package com.jornal.region;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code jornal.region.code}: país que atiende esta instancia (variable APP_REGION). */
@Validated
@ConfigurationProperties("jornal.region")
public record RegionProperties(@NotBlank @Pattern(regexp = "[A-Z]{2}") String code) {
}
