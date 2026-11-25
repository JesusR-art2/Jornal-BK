package com.jornal.vacancies.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vacante que publica la empresa.
 *
 * @param description qué hay que hacer (comentario del empleador)
 * @param items       implementos que debe llevar el trabajador (puede ir vacío)
 * @param startLocal  día y hora locales de inicio, en {@code timezone} (si falta, la zona por defecto del país)
 * @param endLocal    hora local de finalización; después de ella el empleador cierra a cada persona
 * @param positions   número de personas que necesita
 * @param payMinor    pago por persona en unidad menor de la moneda del país
 * @param address     campos de la dirección según el esquema del país
 */
public record VacancyDraft(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        UUID categoryId,
        @Size(max = 20) List<@NotBlank @Size(max = 120) String> items,
        @NotNull LocalDateTime startLocal,
        @NotNull LocalDateTime endLocal,
        @Size(max = 40) String timezone,
        @NotNull @Min(1) @Max(200) Integer positions,
        @NotNull @PositiveOrZero Long payMinor,
        @NotEmpty Map<@NotBlank String, @Size(max = 200) String> address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {
}
